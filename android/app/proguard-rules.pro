# ---- IVRTOP Dialer release rules ----

# PJSUA2 (SWIG/JNI) – classes and callbacks are looked up by name from native code.
-keep class org.pjsip.** { *; }
-keepclassmembers class org.pjsip.** { *; }
-keep class com.ivrtop.dialer.telephony.sipengine.** { *; }

# kotlinx.serialization – keep generated serializers for @Serializable models / nav routes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.ivrtop.dialer.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.ivrtop.dialer.**$$serializer { *; }
-keepclassmembers class com.ivrtop.dialer.** { *** Companion; }

# minidns uses reflection for record types
-keep class org.minidns.record.** { *; }

# Strip verbose/debug logging from release builds (defence in depth – Timber tree also filters).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
}
