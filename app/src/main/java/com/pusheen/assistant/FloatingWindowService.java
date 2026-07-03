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
import android.util.DisplayMetrics;
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
 * 悬浮窗服务 - 右侧窄条悬浮，不遮挡输入框
 */
public class FloatingWindowService extends Service {

    private static final String TAG = "FloatingWindow";
    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";

    // 窗口尺寸
    private static final int WINDOW_WIDTH_DP = 320;
    private static final int WINDOW_MAX_HEIGHT_DP = 500;

    private WindowManager windowManager;
    private View floatingView;
    private View panelMain;       // 主面板（展开）
    private View btnExpand;       // 收起按钮
    private TextView tvStatus;
    private TextView tvEmpty;
    private LinearLayout layoutResults;

    private ExecutorService executor;
    private Handler mainHandler;

    // 拖动相关
    private float initialTouchX, initialTouchY;
    private int initialX, initialY;
    private boolean isDragging = false;

    // 展开状态
    private boolean isExpanded = true;

    // 单例引用
    private static FloatingWindowService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        // Android 8+ 前台通知
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
     * 从无障碍服务触发话术生成
     */
    public static void triggerGenerate(android.content.Context context, String message) {
        if (instance != null) {
            instance.generateReplies(message);
        } else {
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
     * 创建悬浮窗 - 右侧悬浮，不挡输入框
     */
    private void createFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 获取屏幕信息，dp转px
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        float density = metrics.density;
        int windowWidthPx = (int) (WINDOW_WIDTH_DP * density);
        int windowHeightPx = (int) (WINDOW_MAX_HEIGHT_DP * density);

        // 悬浮窗布局
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null);

        // 绑定视图
        panelMain = floatingView.findViewById(R.id.panel_main);
        btnExpand = floatingView.findViewById(R.id.btn_expand);
        tvStatus = floatingView.findViewById(R.id.tv_status);
        tvEmpty = floatingView.findViewById(R.id.tv_empty);
        layoutResults = floatingView.findViewById(R.id.layout_results);
        ImageButton btnClose = floatingView.findViewById(R.id.btn_close);
        ImageButton btnCollapse = floatingView.findViewById(R.id.btn_collapse);
        View dragBar = floatingView.findViewById(R.id.drag_bar);

        // ===== 窗口参数 =====
        WindowManager.LayoutParams params;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params = new WindowManager.LayoutParams(
                    windowWidthPx,
                    windowHeightPx,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
        } else {
            params = new WindowManager.LayoutParams(
                    windowWidthPx,
                    windowHeightPx,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
        }

        // 关键：右侧悬浮，不挡底部输入框
        params.gravity = Gravity.RIGHT | Gravity.TOP;
        params.x = 10;   // 距右边缘间距(dp会根据密度自动换算)
        params.y = 120;  // 距顶部距离

        // ===== 关闭按钮 =====
        btnClose.setOnClickListener(v -> hideFloatingWindow());

        // ===== 收起/展开切换 =====
        btnCollapse.setOnClickListener(v -> collapse());
        btnExpand.setOnClickListener(v -> expand());

        // ===== 整个标题栏可拖动 =====
        dragBar.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isDragging = false;
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        // 超过阈值才算拖动（区分点击和拖动）
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            isDragging = true;
                            params.x = initialX + (int) dx;
                            params.y = initialY + (int) dy;
                            // 边界限制：不拖出屏幕
                            params.y = Math.max(0, Math.min(params.y, metrics.heightPixels - 200));
                            windowManager.updateViewLayout(floatingView, params);
                        }
                        return true;

                    case MotionEvent.UP:
                        // 如果没拖动过，算点击（不做任何事就行）
                        return isDragging;
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

    /** 收起到小按钮 */
    private void collapse() {
        isExpanded = false;
        panelMain.setVisibility(View.GONE);
        btnExpand.setVisibility(View.VISIBLE);
        // 收起时缩小窗口到按钮大小
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) floatingView.getLayoutParams();
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        params.width = (int) (48 * metrics.density);
        params.height = (int) (48 * metrics.density);
        windowManager.updateViewLayout(floatingView, params);
    }

    /** 从按钮展开 */
    private void expand() {
        isExpanded = true;
        btnExpand.setVisibility(View.GONE);
        panelMain.setVisibility(View.VISIBLE);
        // 恢复正常尺寸
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) floatingView.getLayoutParams();
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        params.width = (int) (WINDOW_WIDTH_DP * metrics.density);
        params.height = (int) (WINDOW_MAX_HEIGHT_DP * metrics.density);
        windowManager.updateViewLayout(floatingView, params);
    }

    /**
     * 生成话术（调用 DeepSeek API）
     */
    private void generateReplies(String userMessage) {
        // 确保展开状态
        if (!isExpanded) {
            expand();
        }

        mainHandler.post(() -> {
            tvStatus.setText("⏳ 生成中...");
            tvEmpty.setVisibility(View.GONE);
            layoutResults.setVisibility(View.VISIBLE);
            layoutResults.removeAllViews();

            // 加载占位提示
            TextView loading = new TextView(FloatingWindowService.this);
            loading.setText("正在分析消息，生成话术...");
            loading.setPadding(16, 20, 16, 20);
            loading.setGravity(android.view.Gravity.CENTER);
            loading.setTextColor(0xFF999999);
            loading.setTextSize(13);
            layoutResults.addView(loading);
        });

        String apiKey = ConfigManager.getApiKey(this);
        if (apiKey == null || apiKey.isEmpty()) {
            mainHandler.post(() -> {
                tvStatus.setText("❌ 未配置Key");
                layoutResults.removeAllViews();
                TextView err = new TextView(FloatingWindowService.this);
                err.setText("请先在设置中配置API Key");
                err.setPadding(16, 20, 16, 20);
                err.setGravity(android.view.Gravity.CENTER);
                err.setTextColor(0xFFFF4444);
                err.setTextSize(13);
                layoutResults.addView(err);
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

                OutputStream os = conn.getOutputStream();
                os.write(requestBody.toString().getBytes("UTF-8"));
                os.close();

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

                mainHandler.post(() -> displayResults(content));

            } catch (Exception e) {
                Log.e(TAG, "API调用失败", e);
                mainHandler.post(() -> {
                    tvStatus.setText("❌ 生成失败");
                    layoutResults.removeAllViews();
                    TextView err = new TextView(FloatingWindowService.this);
                    err.setText("网络错误：" + e.getMessage());
                    err.setPadding(16, 12, 16, 12);
                    err.setTextColor(0xFFFF4444);
                    err.setTextSize(12);
                    layoutResults.addView(err);
                });
            }
        });
    }

    /**
     * 显示话术结果
     */
    private void displayResults(String content) {
        tvStatus.setText("✅ 已生成（点卡片复制）");
        layoutResults.removeAllViews();

        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 清理序号前缀
            String cleanLine = line.replaceAll("^\\d+[\\.\\、\\s:：]+", "").trim();
            if (cleanLine.isEmpty()) continue;

            // 创建可复制的话术卡片
            TextView tv = new TextView(this);
            tv.setText(cleanLine);
            tv.setPadding(20, 14, 20, 14);
            tv.setBackgroundResource(R.drawable.bg_reply_card);
            tv.setTextColor(0xFF222222);
            tv.setTextSize(14f);
            tv.setMaxLines(4);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setLineSpacing(4f, 1f);

            // 点击复制
            tv.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("话术", cleanLine);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(FloatingWindowService.this, "已复制 ✅", Toast.LENGTH_SHORT).show();
                // 复制后给个视觉反馈
                tv.setBackgroundColor(0xFFD4EDDA);
                mainHandler.postDelayed(() -> tv.setBackgroundResource(R.drawable.bg_reply_card), 300);
            });

            // 长按显示完整内容
            tv.setOnLongClickListener(v -> {
                // 用对话框显示完整话术
                new android.app.AlertDialog.Builder(FloatingWindowService.this)
                        .setTitle("完整话术")
                        .setMessage(cleanLine)
                        .setPositiveButton("复制", (d, w) -> {
                            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            cb.setPrimaryClip(ClipData.newPlainText("话术", cleanLine));
                            Toast.makeText(FloatingWindowService.this, "已复制", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("关闭", null)
                        .show();
                return true;
            });

            layoutResults.addView(tv);

            // 卡片之间加间距
            View spacer = new View(this);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 6));
            layoutResults.addView(spacer);
        }
    }

    private void hideFloatingWindow() {
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
            floatingView = null;
        }
        stopSelf();
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
