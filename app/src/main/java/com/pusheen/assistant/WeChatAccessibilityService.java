package com.pusheen.assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 微信无障碍服务
 * 监听微信/企微聊天消息，自动触发话术生成
 */
public class WeChatAccessibilityService extends AccessibilityService {

    private static final String TAG = "WeChatAccessibility";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String WEWORK_PACKAGE = "com.tencent.wework";

    // 最近处理的消息，避免重复
    private String lastMessage = "";
    private long lastProcessTime = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "无障碍服务已连接");
        Toast.makeText(this, "普微助手已启动", Toast.LENGTH_SHORT).show();

        // 配置服务信息
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = AccessibilityServiceInfo.DEFAULT
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.packageNames = new String[]{WECHAT_PACKAGE, WEWORK_PACKAGE};
            info.notificationTimeout = 100;
            setServiceInfo(info);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        String pkg = event.getPackageName().toString();
        // 只处理微信和企微
        if (!pkg.equals(WECHAT_PACKAGE) && !pkg.equals(WEWORK_PACKAGE)) return;

        // 获取当前聊天消息
        String latestMessage = findLatestMessage();
        if (latestMessage == null || latestMessage.isEmpty()) return;

        // 去重：相同消息1秒内不重复处理
        long now = System.currentTimeMillis();
        if (latestMessage.equals(lastMessage) && (now - lastProcessTime) < 1000) return;

        lastMessage = latestMessage;
        lastProcessTime = now;

        Log.d(TAG, "捕获消息: " + latestMessage);

        // 发送到悬浮窗服务进行话术生成
        FloatingWindowService.triggerGenerate(this, latestMessage);
    }

    /**
     * 查找最新的聊天消息
     * 通过遍历无障碍节点树来查找聊天气泡文字
     */
    private String findLatestMessage() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;

        List<String> messages = new ArrayList<>();
        traverseNodes(root, messages);
        root.recycle();

        if (messages.isEmpty()) return null;
        // 返回最后一条消息
        return messages.get(messages.size() - 1);
    }

    /**
     * 遍历节点树，收集所有文本消息
     * 微信聊天气泡通常包含 TextView 节点
     */
    private void traverseNodes(AccessibilityNodeInfo node, List<String> messages) {
        if (node == null) return;

        String className = node.getClassName() != null ? node.getClassName().toString() : "";

        // 查找 TextView 中的文本
        if ("android.widget.TextView".equals(className)) {
            CharSequence text = node.getText();
            if (text != null && text.length() > 1) {
                String msg = text.toString().trim();
                // 过滤掉明显的非消息内容（如时间、系统提示等）
                if (!msg.matches("\\d{1,2}:\\d{2}")  // 过滤时间
                        && !msg.contains("微信")       // 过滤系统提示
                        && msg.length() > 1) {
                    messages.add(msg);
                }
            }
        }

        // 递归遍历子节点
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseNodes(child, messages);
                child.recycle();
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "无障碍服务被中断");
    }
}
