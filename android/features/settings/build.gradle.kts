plugins {
    alias(libs.plugins.ivrtop.android.feature)
}

android {
    namespace = "com.ivrtop.dialer.feature.settings"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.androidx.appcompat)
}
