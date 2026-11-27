plugins {
    alias(libs.plugins.ivrtop.android.feature)
}

android {
    namespace = "com.ivrtop.dialer.feature.onboarding"
}

dependencies {
    // Open-source QR scanner (Apache-2.0, ZXing). Asks for CAMERA only when the user taps "scan".
    implementation(libs.zxing.android.embedded)
    implementation(libs.androidx.activity.compose)
}
