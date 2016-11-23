package com.openxu.camera;

import android.app.Application;

import com.nostra13.universalimageloader.cache.disc.naming.Md5FileNameGenerator;
import com.nostra13.universalimageloader.cache.memory.impl.WeakMemoryCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;

/**
 */
public class MidCompanyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();


        // imageLoader配置
        DisplayImageOptions imageOptions = new DisplayImageOptions.Builder()
                .cacheInMemory(true).cacheOnDisc(true).build();
        ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(
                this).defaultDisplayImageOptions(imageOptions)
                .discCacheFileNameGenerator(new Md5FileNameGenerator())
                .memoryCacheSize(2 * 1024 * 1024)
                .memoryCache(new WeakMemoryCache())
                .build();
        ImageLoader instance = ImageLoader.getInstance();
        instance.init(config);

    }





}
