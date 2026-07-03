package com.pusheen.assistant;

import android.app.Application;

/**
 * Application 类
 * 在应用启动时初始化
 */
public class PusheenApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // 不在 Application 中启动服务，改为用户手动启动
        // 避免 Android 8+ 后台服务限制
    }
}
