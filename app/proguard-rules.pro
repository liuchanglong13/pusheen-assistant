# proguard-rules.pro
# 保持 AccessibilityService 不被混淆
-keep public class * extends android.accessibilityservice.AccessibilityService {
    public <methods>;
}

# 保持 Service 不被混淆
-keep public class * extends android.app.Service {
    public <methods>;
}

# 保持 Application 类不被混淆
-keep public class * extends android.app.Application

# 保持 BroadcastReceiver 不被混淆
-keep public class * extends android.content.BroadcastReceiver {
    public <methods>;
}

# JSON 相关（如果需要）
-keepattributes Signature
-keepattributes *Annotation*
-keep class org.json.** { *; }

# 保持 View 类不被混淆
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}
