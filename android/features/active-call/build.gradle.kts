plugins {
    alias(libs.plugins.ivrtop.android.feature)
}

android {
    namespace = "com.ivrtop.dialer.feature.activecall"
}

dependencies {
    implementation(project(":features:incoming-call"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
}
