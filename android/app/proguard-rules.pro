# Samsung S Pen Remote SDK ships as plain jars with no consumer rules. Keep it whole: it talks
# to the system S Pen service through AIDL-generated classes that R8 would otherwise strip.
-keep class com.samsung.android.sdk.penremote.** { *; }

# Galaxy firmware provides this class at runtime; it isn't in the public Android SDK.
-dontwarn com.samsung.android.feature.**
