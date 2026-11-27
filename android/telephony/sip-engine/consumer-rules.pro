# PJSUA2 SWIG classes are called from JNI by name – keep everything.
-keep class org.pjsip.** { *; }
-keepclassmembers class org.pjsip.** { *; }
-keep class com.ivrtop.dialer.telephony.sipengine.Pj* { *; }
