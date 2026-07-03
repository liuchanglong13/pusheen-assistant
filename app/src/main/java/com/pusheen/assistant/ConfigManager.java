package com.pusheen.assistant;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 配置管理类
 * 管理 API Key、模型、系统提示词等配置
 */
public class ConfigManager {

    private static final String PREFS_NAME = "pusheen_config";
    private static final String KEY_API_KEY = "deepseek_api_key";
    private static final String KEY_API_URL = "deepseek_api_url";
    private static final String KEY_MODEL = "deepseek_model";
    private static final String KEY_SYSTEM_PROMPT = "system_prompt";
    private static final String KEY_AUTO_GENERATE = "auto_generate";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String getApiKey(Context context) {
        return getPrefs(context).getString(KEY_API_KEY, "sk-9975e9827a7f4003bbe490d81addbb5b");
    }

    public static void setApiKey(Context context, String apiKey) {
        getPrefs(context).edit().putString(KEY_API_KEY, apiKey).apply();
    }

    public static String getApiUrl(Context context) {
        return getPrefs(context).getString(KEY_API_URL, "https://api.deepseek.com/v1/chat/completions");
    }

    public static void setApiUrl(Context context, String apiUrl) {
        getPrefs(context).edit().putString(KEY_API_URL, apiUrl).apply();
    }

    public static String getModel(Context context) {
        return getPrefs(context).getString(KEY_MODEL, "deepseek-chat");
    }

    public static void setModel(Context context, String model) {
        getPrefs(context).edit().putString(KEY_MODEL, model).apply();
    }

    public static String getSystemPrompt(Context context) {
        String defaultPrompt = "你是一个专业的销售话术助手。根据客户的消息，生成3条高质量回复话术。\n" +
                "要求：\n" +
                "1) 简洁有力\n" +
                "2) 针对客户痛点\n" +
                "3) 有温度不机械\n" +
                "每条话术用数字序号标注，每条不超过80字。";
        return getPrefs(context).getString(KEY_SYSTEM_PROMPT, defaultPrompt);
    }

    public static void setSystemPrompt(Context context, String prompt) {
        getPrefs(context).edit().putString(KEY_SYSTEM_PROMPT, prompt).apply();
    }

    public static boolean isAutoGenerate(Context context) {
        return getPrefs(context).getBoolean(KEY_AUTO_GENERATE, true);
    }

    public static void setAutoGenerate(Context context, boolean auto) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_GENERATE, auto).apply();
    }

    /**
     * 检查配置是否完整
     */
    public static boolean isConfigComplete(Context context) {
        String key = getApiKey(context);
        return key != null && key.startsWith("sk-") && key.length() > 20;
    }
}
