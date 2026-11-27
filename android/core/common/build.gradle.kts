plugins {
    alias(libs.plugins.ivrtop.android.library)
    alias(libs.plugins.ivrtop.android.hilt)
}

android {
    namespace = "com.ivrtop.dialer.core.common"
}

dependencies {
    api(project(":core:models"))
    api(libs.libphonenumber)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    api(libs.timber)
}
