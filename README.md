# 普微助手 - 安卓版

桌面悬浮窗 + 微信聊天监听 + DeepSeek 话术生成

## 功能

- 无障碍服务监听微信/企微聊天消息
- 悬浮窗常驻微信界面上方，显示生成的话术
- 点击话术自动复制到剪贴板
- 配置 DeepSeek API Key 即可使用

## 使用步骤

### 1. 用 Android Studio 打开项目
- 安装 Android Studio（https://developer.android.com/studio）
- 打开 `pusheen-android/` 目录
- 等待 Gradle 同步完成

### 2. 连接手机或启动模拟器
- 手机开启"开发者选项" → "USB 调试"
- 或用 Android Studio 自带模拟器

### 3. 运行安装
- 点 Android Studio 的 ▶ Run 按钮
- 首次安装后需要授权两个权限

### 4. 授权权限
打开 App 后依次开启：
1. **无障碍服务** → 在列表中找到"普微助手"并开启
2. **悬浮窗权限** → 允许"普微助手"在其他应用上层显示

### 5. 填入 API Key
- 在 App 主界面填入 DeepSeek API Key
- 点"保存配置"
- 点"测试生成"验证是否正常工作

### 6. 开始使用
- 打开微信/企微，和客户聊天
- 客户发来消息后，悬浮窗会自动显示3条话术
- 点哪条话术，自动复制到剪贴板
- 回到微信粘贴回复

## 目录结构

```
pusheen-android/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/pusheen/assistant/
│       │   ├── MainActivity.java          # 配置界面
│       │   ├── FloatingWindowService.java # 悬浮窗服务
│       │   ├── WeChatAccessibilityService.java # 微信消息监听
│       │   ├── ConfigManager.java       # 配置管理
│       │   ├── PusheenApp.java        # Application 类
│       │   └── BootReceiver.java       # 开机自启
│       ├── res/
│       │   ├── layout/                 # 界面布局
│       │   ├── drawable/               # 图标和背景
│       │   ├── values/                 # 颜色、样式
│       │   ├── xml/                    # 无障碍配置
│       │   └── mipmap-*/             # 启动图标
│       └── assets/
├── build.gradle
└── settings.gradle
```

## 注意事项

- **微信封号风险**：使用无障碍服务读取微信消息，存在被微信检测并封号的风险，请谨慎使用。
- **仅限安卓**：iOS 不支持无障碍服务和悬浮窗，无法使用。
- **DeepSeek API Key**：需要自行申请（https://platform.deepseek.com）
