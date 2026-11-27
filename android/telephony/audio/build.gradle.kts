plugins {
    alias(libs.plugins.ivrtop.android.library)
    alias(libs.plugins.ivrtop.android.hilt)
}

android {
    namespace = "com.ivrtop.dialer.telephony.audio"
}

dependencies {
    implementation(project(":telephony:sip-api"))
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
}
