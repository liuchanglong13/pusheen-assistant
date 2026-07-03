package com.pusheen.assistant;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * 主配置界面
 * 用户在此配置 API Key、模型、提示词，并开启权限
 */
public class MainActivity extends AppCompatActivity {

    private EditText etApiKey;
    private EditText etApiUrl;
    private Spinner spModel;
    private EditText etPrompt;
    private Switch swAutoGenerate;
    private Button btnSave;
    private Button btnTest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        loadConfig();
        setupListeners();
        checkPermissions();
    }

    private void initViews() {
        etApiKey = findViewById(R.id.et_api_key);
        etApiUrl = findViewById(R.id.et_api_url);
        spModel = findViewById(R.id.sp_model);
        etPrompt = findViewById(R.id.et_prompt);
        swAutoGenerate = findViewById(R.id.sw_auto_generate);
        btnSave = findViewById(R.id.btn_save);
        btnTest = findViewById(R.id.btn_test);

        // 模型选择
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"deepseek-chat", "deepseek-reasoner"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spModel.setAdapter(adapter);
    }

    private void loadConfig() {
        etApiKey.setText(ConfigManager.getApiKey(this));
        etApiUrl.setText(ConfigManager.getApiUrl(this));

        String model = ConfigManager.getModel(this);
        if ("deepseek-reasoner".equals(model)) {
            spModel.setSelection(1);
        }

        etPrompt.setText(ConfigManager.getSystemPrompt(this));
        swAutoGenerate.setChecked(ConfigManager.isAutoGenerate(this));
    }

    private void setupListeners() {
        btnSave.setOnClickListener(v -> saveConfig());
        btnTest.setOnClickListener(v -> testGenerate());

        // 权限按钮
        findViewById(R.id.btn_accessibility).setOnClickListener(v -> openAccessibilitySettings());
        findViewById(R.id.btn_overlay).setOnClickListener(v -> openOverlaySettings());
    }

    private void saveConfig() {
        String apiKey = etApiKey.getText().toString().trim();
        String apiUrl = etApiUrl.getText().toString().trim();
        String model = spModel.getSelectedItem().toString();
        String prompt = etPrompt.getText().toString().trim();
        boolean auto = swAutoGenerate.isChecked();

        if (apiKey.isEmpty() || !apiKey.startsWith("sk-")) {
            Toast.makeText(this, "请输入有效的 DeepSeek API Key", Toast.LENGTH_SHORT).show();
            return;
        }

        ConfigManager.setApiKey(this, apiKey);
        ConfigManager.setApiUrl(this, apiUrl);
        ConfigManager.setModel(this, model);
        ConfigManager.setSystemPrompt(this, prompt);
        ConfigManager.setAutoGenerate(this, auto);

        Toast.makeText(this, "✅ 配置已保存", Toast.LENGTH_SHORT).show();

        // 启动悬浮窗服务（Android 8+ 用前台服务）
        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void testGenerate() {
        String apiKey = etApiKey.getText().toString().trim();
        if (apiKey.isEmpty() || !apiKey.startsWith("sk-")) {
            Toast.makeText(this, "请先填写 API Key", Toast.LENGTH_SHORT).show();
            return;
        }

        // 保存配置
        saveConfig();

        // 启动悬浮窗服务并测试（Android 8+ 用前台服务）
        Intent intent = new Intent(this, FloatingWindowService.class);
        intent.setAction("generate");
        intent.putExtra("message", "你好，我想了解一下你们的产品");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        Toast.makeText(this, "已触发测试，请查看悬浮窗", Toast.LENGTH_SHORT).show();
    }

    /**
     * 检查权限状态
     */
    private void checkPermissions() {
        // 检查悬浮窗权限
        boolean hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(this);
        TextView tvOverlay = findViewById(R.id.tv_overlay_status);
        tvOverlay.setText(hasOverlay ? "✅ 已授权" : "❌ 未授权");
        tvOverlay.setTextColor(hasOverlay ? 0xFF4CAF50 : 0xFFF44336);

        // 检查无障碍权限
        boolean hasAccessibility = isAccessibilityServiceEnabled();
        TextView tvAccess = findViewById(R.id.tv_accessibility_status);
        tvAccess.setText(hasAccessibility ? "✅ 已启用" : "❌ 未启用");
        tvAccess.setTextColor(hasAccessibility ? 0xFF4CAF50 : 0xFFF44336);
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabled = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (AccessibilityServiceInfo info : enabled) {
            if (info.getResolveInfo().serviceInfo.packageName.equals(getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "请在列表中找到「普微助手」并开启", Toast.LENGTH_LONG).show();
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "请允许「普微助手」在其他应用上层显示", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
    }
}
