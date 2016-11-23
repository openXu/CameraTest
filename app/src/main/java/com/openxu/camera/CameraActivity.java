package com.openxu.camera;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.openxu.camera.camerautil.CameraHelper;
import com.openxu.camera.camerautil.ImageUtils;
import com.openxu.camera.camerautil.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;


/**
 * 相机界面
 * Created by sky on 15/7/6.
 */
public class CameraActivity extends Activity {
    private String TAG = "CameraActivity";

    private int screenWidth, screenHeight;   //屏幕宽高

    private static final int MIN_PREVIEW_PIXELS = 480 * 320;   //最小预览界面的分辨率
    private static final double MAX_ASPECT_DISTORTION = 0.15;  //最大宽高比差

    private Handler handler = new Handler();
    private float pointX, pointY;  //手指按下的点（焦点）
    static final int FOCUS = 1;            // 聚焦
    static final int ZOOM = 2;            // 缩放
    private int mode;                      //0是聚焦 1是放大
    private float dist;
    //拍照相关
    private Bundle bundle = null;
    private int PHOTO_SIZE = 2000;
    private String CAMERA_PATH;
    private List<String> listPath = new ArrayList<String>();//存放路径的list
    //展示照片
    private int ll_photos_height;   //照片容器的高度
    private int photoWidth;         //照片宽高
    private int photoNumber = 4;    //照片数量
    private int photoMargin;        //左右距离

    private CameraHelper mCameraHelper;
    private Camera.Parameters parameters = null;
    private Camera cameraInst = null;

    SurfaceView surfaceView;
    CameraGrid cameraLin;
    LinearLayout ll_photos;    //照片容器
    RelativeLayout rl_ctrl_out, rl_ctrl; //控制按钮
    View focusView;          //焦点
    Button btn_takePhoto;
    ImageView iv_flash, iv_change;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        mCameraHelper = new CameraHelper(this);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        screenWidth = displayMetrics.widthPixels;
        screenHeight = displayMetrics.heightPixels;
        Log.v(TAG, "屏幕宽高："+screenWidth+" * "+screenHeight);  //屏幕宽高：1080 * 1920
        CAMERA_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera";

        surfaceView = (SurfaceView)findViewById(R.id.surfaceView);
        cameraLin = (CameraGrid)findViewById(R.id.cameraLin);
        ll_photos = (LinearLayout) findViewById(R.id.ll_photos);
        rl_ctrl_out = (RelativeLayout) findViewById(R.id.rl_ctrl_out);
        rl_ctrl = (RelativeLayout) findViewById(R.id.rl_ctrl);
        focusView = findViewById(R.id.focusView);
        btn_takePhoto = (Button)findViewById(R.id.btn_takePhoto);
        iv_flash = (ImageView)findViewById(R.id.iv_flash);
        iv_change = (ImageView)findViewById(R.id.iv_change);

        initView();
        initEvent();
    }

    private void initView() {
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceHolder.setKeepScreenOn(true);
        surfaceView.setFocusable(true);
        surfaceView.setBackgroundColor(TRIM_MEMORY_BACKGROUND);
        surfaceView.getHolder().addCallback(new SurfaceCallback());//为SurfaceView的句柄添加一个回调函数

        //设置相机界面,照片列表,以及拍照布局的高度(保证相机预览为正方形)
        //参考线高度为屏幕宽度
        ViewGroup.LayoutParams layout = cameraLin.getLayoutParams();
        layout.height = screenWidth;
        //相片容器高度
        layout = ll_photos.getLayoutParams();
        photoWidth = screenWidth / 4 - DensityUtil.dip2px(this, 8);  //照片宽高
        photoMargin = DensityUtil.dip2px(this, 4);                   //照片左右距离
        ll_photos_height = photoWidth + DensityUtil.dip2px(this, 16);    //容器高度
        layout.height = ll_photos_height;
        //控制按钮面板高度
        layout = rl_ctrl.getLayoutParams();
        int ctrlHight= screenHeight -screenWidth - ll_photos_height;
        layout.height = ctrlHight;
        //示例按钮
        layout = rl_ctrl_out.getLayoutParams();
        layout.height = ctrlHight + DensityUtil.dip2px(this, 30);

        Log.v(TAG, "照片容器高度："+ll_photos_height+"控制按钮高度："+ctrlHight +",相机高度："+screenWidth);

    }
    private void initEvent() {
        //拍照
        btn_takePhoto.setOnClickListener(v -> {
            try {
                cameraInst.takePicture(null, null, new MyPictureCallback());
            } catch (Throwable t) {
                t.printStackTrace();
                Toast.makeText(CameraActivity.this, "拍照失败，请重试！", Toast.LENGTH_SHORT).show();
                try {
                    cameraInst.startPreview();
                } catch (Throwable e) {

                }
            }

        });

        //闪光灯
        iv_flash.setOnClickListener(v -> turnLight(cameraInst));
        //前后置摄像头切换
        boolean canSwitch = false;
        try {
            canSwitch = mCameraHelper.hasFrontCamera() && mCameraHelper.hasBackCamera();
        } catch (Exception e) {
            //获取相机信息失败
        }
        if (!canSwitch) {
            iv_change.setVisibility(View.GONE);
        } else {
            iv_change.setOnClickListener(v -> switchCamera());
       }
        //跳转相册
//        galleryBtn.setOnClickListener(v -> startActivity(new Intent(CameraActivity.this, AlbumActivity.class)));
        //返回按钮
//        backBtn.setOnClickListener(v -> finish());
        surfaceView.setOnTouchListener((v, event) -> {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                // 主点按下
                case MotionEvent.ACTION_DOWN:
                    pointX = event.getX();
                    pointY = event.getY() - ll_photos_height;
                    Log.v(TAG, "按下："+pointX+"   "+pointY);
                    mode = FOCUS;
                    break;
                // 副点按下
                case MotionEvent.ACTION_POINTER_DOWN:
                    dist = spacing(event);
                    // 如果连续两点距离大于10，则判定为多点模式
                    if (spacing(event) > 10f) {
                        mode = ZOOM;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    mode = FOCUS;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mode == FOCUS) {
                        //pointFocus((int) event.getRawX(), (int) event.getRawY());
                    } else if (mode == ZOOM) {
                        float newDist = spacing(event);
                        if (newDist > 10f) {
                            float tScale = (newDist - dist) / dist;
                            if (tScale < 0) {
                                tScale = tScale * 10;
                            }
                            addZoomIn((int) tScale);
                        }
                    }
                    break;
            }
            return false;
        });

        surfaceView.setOnClickListener(v -> {
            try {
                pointFocus((int) pointX, (int) pointY);
            } catch (Exception e) {
                e.printStackTrace();
            }
            RelativeLayout.LayoutParams layout = new RelativeLayout.LayoutParams(focusView.getLayoutParams());
            layout.setMargins((int) pointX - 60, (int) pointY - 60, 0, 0);
            focusView.setLayoutParams(layout);
            focusView.setVisibility(View.VISIBLE);
            ScaleAnimation sa = new ScaleAnimation(3f, 1f, 3f, 1f,
                    ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
            sa.setDuration(800);
            focusView.startAnimation(sa);
            handler.postDelayed(() -> focusView.setVisibility(View.INVISIBLE), 800);
        });
/*
        takePhotoPanel.setOnClickListener(v -> {
            //doNothing 防止聚焦框出现在拍照区域
        });*/

    }



    /*SurfaceCallback*/
    private final class SurfaceCallback implements SurfaceHolder.Callback {

        public void surfaceDestroyed(SurfaceHolder holder) {
            try {
                if (cameraInst != null) {
                    cameraInst.stopPreview();
                    cameraInst.release();
                    cameraInst = null;
                }
            } catch (Exception e) {
                //相机已经关了
            }

        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            if (null == cameraInst) {
                try {
                    cameraInst = Camera.open();
                    cameraInst.setPreviewDisplay(holder);
                    initCamera();
                    cameraInst.startPreview();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            autoFocus();
        }
    }

    /******************************相机设置*******************************/
    private Camera.Size adapterSize = null;
    private Camera.Size previewSize = null;
    private void initCamera() {
        parameters = cameraInst.getParameters();
        parameters.setPictureFormat(PixelFormat.JPEG);
        //if (adapterSize == null) {
        setUpPreviewSize(parameters);
        setUpPicSize(parameters);
        //}
        if (adapterSize != null) {
            parameters.setPictureSize(adapterSize.width, adapterSize.height);
        }
        if (previewSize != null) {
            parameters.setPreviewSize(previewSize.width, previewSize.height);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);//1连续对焦
        } else {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        setDispaly(parameters, cameraInst);
        try {
            cameraInst.setParameters(parameters);
        } catch (Exception e) {
            e.printStackTrace();
        }
        cameraInst.startPreview();
        cameraInst.cancelAutoFocus();// 2如果要实现连续的自动对焦，这一句必须加上
    }

    private void setUpPicSize(Camera.Parameters parameters) {

        if (adapterSize != null) {
            return;
        } else {
            adapterSize = findBestPictureResolution();
            return;
        }
    }

    private void setUpPreviewSize(Camera.Parameters parameters) {
        if (previewSize != null) {
            return;
        } else {
            previewSize = findBestPreviewResolution();
        }
    }

    //找出最适合的预览界面分辨率
    private Camera.Size findBestPreviewResolution() {
        Camera.Parameters cameraParameters = cameraInst.getParameters();
        Camera.Size defPreSize = cameraParameters.getPreviewSize();
        Log.v(TAG, "默认预览分辨率: " + defPreSize.width +"*"+ defPreSize.height);
        List<Camera.Size> rawSupportedSizes = cameraParameters.getSupportedPreviewSizes();
        if (rawSupportedSizes == null) {
            return defPreSize;
        }
        // 按照分辨率从大到小排序
        List<Camera.Size> supportedPreviewResolutions = new ArrayList<Camera.Size>(rawSupportedSizes);
        Collections.sort(supportedPreviewResolutions, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels) {
                    return -1;
                }
                if (bPixels > aPixels) {
                    return 1;
                }
                return 0;
            }
        });

        StringBuilder previewResolutionSb = new StringBuilder();
        for (Camera.Size supportedPreviewResolution : supportedPreviewResolutions) {
            previewResolutionSb.append(supportedPreviewResolution.width).append('x').append(supportedPreviewResolution.height)
                    .append(' ');
        }
        Log.v(TAG, "支持的预览分辨率: " + previewResolutionSb);

        // 移除不符合条件的分辨率
        double screenAspectRatio = (double) screenWidth / (double) screenHeight;
        Iterator<Camera.Size> it = supportedPreviewResolutions.iterator();
        while (it.hasNext()) {
            Camera.Size supPreSize = it.next();
            int width = supPreSize.width;
            int height = supPreSize.height;
            // 移除低于下限的分辨率，尽可能取高分辨率
            if (width * height < MIN_PREVIEW_PIXELS) {
                it.remove();
//                Log.w(TAG, "分辨率太低，剔除: " + width +"*"+ height);
                continue;
            }

            // 在camera分辨率与屏幕分辨率宽高比不相等的情况下，找出差距最小的一组分辨率
            // 由于camera的分辨率是width>height，我们设置的portrait模式中，width<height
            // 因此这里要先交换然preview宽高比后在比较
            boolean isCandidatePortrait = width > height;
            int maybeFlippedWidth = isCandidatePortrait ? height : width;
            int maybeFlippedHeight = isCandidatePortrait ? width : height;
            double aspectRatio = (double) maybeFlippedWidth / (double) maybeFlippedHeight;
            double distortion = Math.abs(aspectRatio - screenAspectRatio);
            if (distortion > MAX_ASPECT_DISTORTION) {
//                Log.w(TAG, "偏差太大，剔除: " + width +"*"+ height);
                it.remove();
                continue;
            }

            // 找到与屏幕分辨率完全匹配的预览界面分辨率直接返回
            if (maybeFlippedWidth == screenWidth && maybeFlippedHeight == screenHeight) {
                Log.v(TAG, "找到完全匹配的预览分辨率: " + maybeFlippedWidth +"*"+ maybeFlippedHeight);
                return supPreSize;
            }
        }

        // 如果没有找到合适的，并且还有候选的像素，则设置其中最大比例的，对于配置比较低的机器不太合适
        if (!supportedPreviewResolutions.isEmpty()) {
            Camera.Size largestPreview = supportedPreviewResolutions.get(0);
            Log.v(TAG, "选择预览分辨率最大的: " + largestPreview.width +"*"+ largestPreview.height);
            return largestPreview;
        }

        // 没有找到合适的，就返回默认的
        Log.v(TAG, "选择默认预览分辨率: " + defPreSize.width +"*"+ defPreSize.height);
        return defPreSize;
    }

    private Camera.Size findBestPictureResolution() {
        Camera.Parameters cameraParameters = cameraInst.getParameters();
        Camera.Size defPicSize = cameraParameters.getPictureSize();
        Log.v(TAG, "默认预览分辨率: " + defPicSize.width +"*"+ defPicSize.height);

        List<Camera.Size> supPicSizes = cameraParameters.getSupportedPictureSizes(); // 至少会返回一个值
        StringBuilder picResolutionSb = new StringBuilder();
        for (Camera.Size supportedPicResolution : supPicSizes) {
            picResolutionSb.append(supportedPicResolution.width).append('x')
                    .append(supportedPicResolution.height).append(" ");
        }
        Log.d(TAG, "支持的图片分辨率: " + picResolutionSb);

        // 排序
        List<Camera.Size> sortedSupPicSizes = new ArrayList<Camera.Size>(
                supPicSizes);
        Collections.sort(sortedSupPicSizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels) {
                    return -1;
                }
                if (bPixels > aPixels) {
                    return 1;
                }
                return 0;
            }
        });

        // 移除不符合条件的分辨率
        double screenAspectRatio = (double) screenWidth / (double) screenHeight;
        Iterator<Camera.Size> it = sortedSupPicSizes.iterator();
        while (it.hasNext()) {
            Camera.Size supPicSize = it.next();
            int width = supPicSize.width;
            int height = supPicSize.height;

            // 在camera分辨率与屏幕分辨率宽高比不相等的情况下，找出差距最小的一组分辨率
            // 由于camera的分辨率是width>height，我们设置的portrait模式中，width<height
            // 因此这里要先交换然后在比较宽高比
            boolean isCandidatePortrait = width > height;
            int maybeFlippedWidth = isCandidatePortrait ? height : width;
            int maybeFlippedHeight = isCandidatePortrait ? width : height;
            double aspectRatio = (double) maybeFlippedWidth / (double) maybeFlippedHeight;
            double distortion = Math.abs(aspectRatio - screenAspectRatio);
            if (distortion > MAX_ASPECT_DISTORTION) {
                it.remove();
//                Log.w(TAG, "偏差太大，剔除: " + width +"*"+ height);
                continue;
            }
        }

        // 如果没有找到合适的，并且还有候选的像素，对于照片，则取其中最大比例的，而不是选择与屏幕分辨率相同的
        if (!sortedSupPicSizes.isEmpty()) {
            Log.v(TAG, "选择最大图片分辨率: " + sortedSupPicSizes.get(0).width
                    +"*"+ sortedSupPicSizes.get(0).height);
            return sortedSupPicSizes.get(0);
        }

        Log.v(TAG, "选择默认图片分辨率: " + defPicSize.width
                +"*"+ defPicSize.height);
        // 没有找到合适的，就返回默认的
        return defPicSize;
    }

    //控制图像的正确显示方向
    private void setDispaly(Camera.Parameters parameters, Camera camera) {
        if (Build.VERSION.SDK_INT >= 8) {
            setDisplayOrientation(camera, 90);
        } else {
            parameters.setRotation(90);
        }
    }

    //实现的图像的正确显示
    private void setDisplayOrientation(Camera camera, int i) {
        Method downPolymorphic;
        try {
            downPolymorphic = camera.getClass().getMethod("setDisplayOrientation",
                    new Class[]{int.class});
            if (downPolymorphic != null) {
                downPolymorphic.invoke(camera, new Object[]{i});
            }
        } catch (Exception e) {
            Log.e("Came_e", "图像出错");
        }
    }


    /*****************************焦点*********************************/
    //两点的距离
    private float spacing(MotionEvent event) {
        if (event == null) {
            return 0;
        }
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float)Math.sqrt(x * x + y * y);
    }

    //实现自动对焦
    private void autoFocus() {
        new Thread() {
            @Override
            public void run() {
                try {
                    sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (cameraInst == null) {
                    return;
                }
                cameraInst.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        if (success) {
                            initCamera();//实现相机的参数初始化
                        }
                    }
                });
            }
        };
    }

    //定点对焦的代码
    private void pointFocus(int x, int y) {
        cameraInst.cancelAutoFocus();
        parameters = cameraInst.getParameters();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            showPoint(x, y);
        }
        cameraInst.setParameters(parameters);
        autoFocus();
    }
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void showPoint(int x, int y) {
        if (parameters.getMaxNumMeteringAreas() > 0) {
            List<Camera.Area> areas = new ArrayList<Camera.Area>();
            //xy变换了
            int rectY = -x * 2000 / screenWidth + 1000;
            int rectX = y * 2000 / screenHeight - 1000;

            int left = rectX < -900 ? -1000 : rectX - 100;
            int top = rectY < -900 ? -1000 : rectY - 100;
            int right = rectX > 900 ? 1000 : rectX + 100;
            int bottom = rectY > 900 ? 1000 : rectY + 100;
            Rect area1 = new Rect(left, top, right, bottom);
            areas.add(new Camera.Area(area1, 800));
            parameters.setMeteringAreas(areas);
        }

        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
    }
    /****************放大缩小****************/
    //放大缩小
    int curZoomValue = 0;
    private void addZoomIn(int delta) {

        try {
            Camera.Parameters params = cameraInst.getParameters();
            Log.d("Camera", "Is support Zoom " + params.isZoomSupported());
            if (!params.isZoomSupported()) {
                return;
            }
            curZoomValue += delta;
            if (curZoomValue < 0) {
                curZoomValue = 0;
            } else if (curZoomValue > params.getMaxZoom()) {
                curZoomValue = params.getMaxZoom();
            }

            if (!params.isSmoothZoomSupported()) {
                params.setZoom(curZoomValue);
                cameraInst.setParameters(params);
                return;
            } else {
                cameraInst.startSmoothZoom(curZoomValue);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /************************闪光灯************************/
    /**
     * 闪光灯开关   开->关->自动
     * @param mCamera
     */
    private void turnLight(Camera mCamera) {
        if (mCamera == null || mCamera.getParameters() == null
                || mCamera.getParameters().getSupportedFlashModes() == null) {
            return;
        }
        Camera.Parameters parameters = mCamera.getParameters();
        String flashMode = mCamera.getParameters().getFlashMode();
        List<String> supportedModes = mCamera.getParameters().getSupportedFlashModes();
        if (Camera.Parameters.FLASH_MODE_OFF.equals(flashMode)
                && supportedModes.contains(Camera.Parameters.FLASH_MODE_ON)) {//关闭状态
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
            mCamera.setParameters(parameters);
            iv_flash.setImageResource(R.drawable.camera_flash_on);
        } else if (Camera.Parameters.FLASH_MODE_ON.equals(flashMode)) {//开启状态
            if (supportedModes.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                iv_flash.setImageResource(R.drawable.camera_flash_auto);
                mCamera.setParameters(parameters);
            } else if (supportedModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                iv_flash.setImageResource(R.drawable.camera_flash_off);
                mCamera.setParameters(parameters);
            }
        } else if (Camera.Parameters.FLASH_MODE_AUTO.equals(flashMode)
                && supportedModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            mCamera.setParameters(parameters);
            iv_flash.setImageResource(R.drawable.camera_flash_off);
        }
    }

    /*********************切换摄像头*********************/
    private int mCurrentCameraId = 0;  //1是前置 0是后置
    //切换前后置摄像头
    private void switchCamera() {
        mCurrentCameraId = (mCurrentCameraId + 1) % mCameraHelper.getNumberOfCameras();
        releaseCamera();
        Log.d("DDDD", "DDDD----mCurrentCameraId" + mCurrentCameraId);
        setUpCamera(mCurrentCameraId);
    }

    private void releaseCamera() {
        if (cameraInst != null) {
            cameraInst.setPreviewCallback(null);
            cameraInst.release();
            cameraInst = null;
        }
        adapterSize = null;
        previewSize = null;
    }

    /**
     * @param mCurrentCameraId2
     */
    private void setUpCamera(int mCurrentCameraId2) {
        cameraInst = getCameraInstance(mCurrentCameraId2);
        if (cameraInst != null) {
            try {
                cameraInst.setPreviewDisplay(surfaceView.getHolder());
                initCamera();
                cameraInst.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this,"切换失败，请重试！", Toast.LENGTH_LONG).show();

        }
    }

    private Camera getCameraInstance(final int id) {
        Camera c = null;
        try {
            c = mCameraHelper.openCamera(id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return c;
    }


    /****************************拍照******************************/

    private final class MyPictureCallback implements Camera.PictureCallback {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            bundle = new Bundle();
            bundle.putByteArray("bytes", data); //将图片字节数据保存在bundle当中，实现数据交换
            new SavePicTask(data).execute();
            camera.startPreview(); // 拍完照后，重新开始预览
        }
    }

    private class SavePicTask extends AsyncTask<Void, Void, String> {
        private byte[] data;

        protected void onPreExecute() {
//            showProgressDialog("处理中...");
        }

        SavePicTask(byte[] data) {
            this.data = data;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                return saveToSDCard(data);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (StringUtils.isNotEmpty(result)) {
//                dismissProgressDialog();
                showPhoto(result);
            } else {
                Toast.makeText(CameraActivity.this, "拍照失败，请稍后重试！", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 将拍下来的照片存放在SD卡中
     * @param data
     * @throws IOException
     */
    public String saveToSDCard(byte[] data) throws IOException {
        Bitmap bitmap;
        //获得图片大小
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        PHOTO_SIZE = options.outHeight > options.outWidth ? options.outWidth : options.outHeight;
        int height = options.outHeight > options.outWidth ? options.outHeight : options.outWidth;
        options.inJustDecodeBounds = false;
        Rect r;
        if (mCurrentCameraId == 1) {
            r = new Rect(height - PHOTO_SIZE, 0, height, PHOTO_SIZE);
        } else {
            r = new Rect(0, 0, PHOTO_SIZE, PHOTO_SIZE);
        }
        try {
            bitmap = decodeRegionCrop(data, r);
        } catch (Exception e) {
            return null;
        }
        String imagePath = ImageUtils.saveToFile(CAMERA_PATH, true, bitmap);
        Log.v(TAG, "照片地址：" + imagePath);
        bitmap.recycle();
        return imagePath;
    }

    private Bitmap decodeRegionCrop(byte[] data, Rect rect) {
        InputStream is = null;
        System.gc();
        Bitmap croppedImage = null;
        try {
            is = new ByteArrayInputStream(data);
            BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is, false);
            try {
                croppedImage = decoder.decodeRegion(rect, new BitmapFactory.Options());
            } catch (IllegalArgumentException e) {
            }
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null)
                    is.close();
            } catch (IOException e) {
            }
        }
        Matrix m = new Matrix();
        m.setRotate(90, PHOTO_SIZE / 2, PHOTO_SIZE / 2);
        if (mCurrentCameraId == 1) {
            m.postScale(1, -1);
        }
        Bitmap rotatedImage = Bitmap.createBitmap(croppedImage, 0, 0, PHOTO_SIZE, PHOTO_SIZE, m, true);
        if (rotatedImage != croppedImage)
            croppedImage.recycle();
        return rotatedImage;
    }


    //展示照片
    private void showPhoto(String path) {
        listPath.add(path);

        ImageView imageView = new ImageView(this);
        /*if (StringUtils.isNotBlank(photoItem.getImageUri())) {
            ImageLoaderUtils.displayLocalImage(photoItem.getImageUri(), photo, null);
        } else {
            photo.setImageResource(R.drawable.default_img);
        }*/
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(photoWidth, photoWidth);
        params.leftMargin = photoMargin;
        params.rightMargin = photoMargin;
        params.gravity = Gravity.CENTER;
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setTag(path);
        ll_photos.addView(imageView, params);
        ImageLoader.getInstance().displayImage("file://"+path, imageView);
        imageView.setOnClickListener(v -> {
            if (v instanceof ImageView && v.getTag() instanceof String) {
               /* CameraManager.getInst().processPhotoItem(CameraActivity.this,
                        new PhotoItem((String) v.getTag(), System.currentTimeMillis()));*/
            }
        });

    }

   /* // 根据路径获取图片
    private Bitmap getImageBitmap(String path) throws FileNotFoundException, IOException {
        Bitmap bmp = null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, opts);
        opts.inSampleSize = ImageTools.computeSampleSize(opts, -1, 150 * 150);//得到缩略图
        opts.inJustDecodeBounds = false;
        try {
            bmp = BitmapFactory.decodeFile(path, opts);
        } catch (OutOfMemoryError e) {
        }
        return bmp;
    }
*/

}
