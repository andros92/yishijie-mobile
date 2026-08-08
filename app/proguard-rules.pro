# ========== 通用 Android ==========
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

# ========== Gson + Retrofit（API 数据模型）==========
-keep class com.junklord.world.api.** { *; }
-keepclassmembers class com.junklord.world.api.** { *; }

# Gson 通过 @SerializedName 序列化，保留字段名
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit 接口不混淆
-keep interface com.junklord.world.api.JunklordApiService { *; }

# ========== 自己的全部代码保留（小米 SDK 反射/回调依赖，不可精简）==========
# -allowaccessmodification 会改变方法可见性，破坏 SDK 反射
# -mergeinterfacesaggressively 会合并 MessageListener/GameMessageHandler 等接口
# 导致 instanceof 检查失败 → 连接时好时坏
-keep class com.junklord.world.** { *; }
-keepclassmembers class com.junklord.world.** { *; }

# ========== 小米穿戴 SDK ==========
-dontwarn com.xiaomi.xms.**
-keep class com.xiaomi.xms.** { *; }
-keep class com.xiaomi.v.** { *; }

# ========== OkHttp ==========
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ========== 防止反射崩溃 ==========
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
-keep class * extends android.app.Application
-keep class * extends androidx.lifecycle.ViewModel
