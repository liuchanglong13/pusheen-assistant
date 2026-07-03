package com.pusheen.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.*;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 悬浮窗服务
 * 在微信界面上方显示话术结果
 */
public class FloatingWindowService extends Service {

    private static final String TAG = "FloatingWindow";
    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";

    private WindowManager windowManager;
    private View floatingView;
    private TextView tvStatus;
    private LinearLayout layoutResults;
    private ExecutorService executor;
    private Handler mainHandler;

    // 单例引用，用于从无障碍服务触发生成
    private static FloatingWindowService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        // Android 8+ 需要前台通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "pusheen_foreground";
            NotificationChannel channel = new NotificationChannel(
                    channelId, "普微助手服务", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("普微助手后台运行通知");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);

            Notification notification = new Notification.Builder(this, channelId)
                    .setContentTitle("普微助手运行中")
                    .setContentText("话术生成服务已启动")
                    .setSmallIcon(R.drawable.ic_launcher)
                    .build();
            startForeground(1, notification);
        }

        createFloatingWindow();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "generate".equals(intent.getAction())) {
            String message = intent.getStringExtra("message");
            if (message != null && !message.isEmpty()) {
                generateReplies(message);
            }
        }
        return START_STICKY;
    }

    /**
     * 从无障碍服务触发话术生成（静态方法）
     */
    public static void triggerGenerate(android.content.Context context, String message) {
        if (instance != null) {
            instance.generateReplies(message);
        } else {
            // 服务未启动，先启动服务
            Intent intent = new Intent(context, FloatingWindowService.class);
            intent.setAction("generate");
            intent.putExtra("message", message);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        }
    }

    /**
     * 创建悬浮窗
     */
    private void createFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 悬浮窗布局
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null);

        // 窗口参数
        WindowManager.LayoutParams params;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
        } else {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
        }

        // 初始位置：底部
        params.gravity = Gravity.BOTTOM;

        // 绑定视图
        tvStatus = floatingView.findViewById(R.id.tv_status);
        layoutResults = floatingView.findViewById(R.id.layout_results);
        ImageButton btnClose = floatingView.findViewById(R.id.btn_close);
        ImageButton btnSettings = floatingView.findViewById(R.id.btn_settings);

        btnClose.setOnClickListener(v -> hideFloatingWindow());
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        // 拖动条
        View dragBar = floatingView.findViewById(R.id.drag_bar);
        dragBar.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            Log.e(TAG, "创建悬浮窗失败", e);
        }
    }

    /**
     * 生成话术（调用 DeepSeek API）
     */
    private void generateReplies(String userMessage) {
        mainHandler.post(() -> {
            tvStatus.setText("⏳ 正在生成话术...");
            layoutResults.removeAllViews();
        });

        String apiKey = ConfigManager.getApiKey(this);
        if (apiKey == null || apiKey.isEmpty()) {
            mainHandler.post(() -> {
                tvStatus.setText("❌ 请先配置 API Key");
            });
            return;
        }

        String systemPrompt = ConfigManager.getSystemPrompt(this);

        executor.execute(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);

                // 构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", ConfigManager.getModel(this));
                JSONArray messages = new JSONArray();
                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                messages.put(sysMsg);
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", userMessage);
                messages.put(userMsg);
                requestBody.put("messages", messages);
                requestBody.put("temperature", 0.8);
                requestBody.put("max_tokens", 800);

                // 发送请求
                OutputStream os = conn.getOutputStream();
                os.write(requestBody.toString().getBytes("UTF-8"));
                os.close();

                // 读取响应
                BufferedReader br = new BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                String content = jsonResponse
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");

                // 显示结果
                mainHandler.post(() -> displayResults(content));

            } catch (Exception e) {
                Log.e(TAG, "API 调用失败", e);
                mainHandler.post(() -> {
                    tvStatus.setText("❌ 生成失败: " + e.getMessage());
                });
            }
        });
    }

    /**
     * 显示生成的话术结果
     */
    private void displayResults(String content) {
        tvStatus.setText("✅ 话术已生成");
        layoutResults.removeAllViews();

        // 按行分割，每行作为一条话术
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 清理序号前缀（如 "1.", "1、", "1 "）
            String cleanLine = line.replaceAll("^\\d+[\\.\\、\\s]+", "").trim();
            if (cleanLine.isEmpty()) continue;

            // 创建话术卡片
            TextView tv = new TextView(this);
            tv.setText(cleanLine);
            tv.setPadding(24, 16, 24, 16);
            tv.setBackgroundResource(R.drawable.bg_reply_card);
            tv.setTextColor(0xFF333333);
            tv.setTextSize(14);
            tv.setMaxLines(4);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);

            // 点击复制
            tv.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("话术", cleanLine);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(FloatingWindowService.this, "已复制", Toast.LENGTH_SHORT).show();
            });

            layoutResults.addView(tv);
        }
    }

    private void hideFloatingWindow() {
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
            floatingView = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        if (executor != null) executor.shutdown();
        hideFloatingWindow();
    }
}
