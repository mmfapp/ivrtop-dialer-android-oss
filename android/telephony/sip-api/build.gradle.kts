plugins {
    alias(libs.plugins.ivrtop.android.library)
}

android {
    namespace = "com.ivrtop.dialer.telephony.sipapi"
}

dependencies {
    api(project(":core:models"))
    implementation(project(":core:common"))
    api(libs.kotlinx.coroutines.core)
}
