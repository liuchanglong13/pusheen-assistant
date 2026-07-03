package com.pusheen.assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机启动接收器
 * 设备重启后自动启动悬浮窗服务
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "设备已重启，启动普微助手服务");

            // 检查是否已配置
            if (ConfigManager.isConfigComplete(context)) {
                // 启动悬浮窗服务
                Intent serviceIntent = new Intent(context, FloatingWindowService.class);
                context.startService(serviceIntent);
            }
        }
    }
}
