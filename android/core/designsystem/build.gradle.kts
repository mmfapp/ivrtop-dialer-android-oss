plugins {
    alias(libs.plugins.ivrtop.android.library)
    alias(libs.plugins.ivrtop.android.compose)
}

android {
    namespace = "com.ivrtop.dialer.core.designsystem"
}

dependencies {
    api(project(":core:models"))
    implementation(project(":core:common"))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons)
    implementation(libs.coil.compose)
    implementation(libs.androidx.core.ktx)
}
