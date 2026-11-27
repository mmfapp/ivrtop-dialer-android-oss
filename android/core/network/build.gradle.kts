plugins {
    alias(libs.plugins.ivrtop.android.library)
    alias(libs.plugins.ivrtop.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ivrtop.dialer.core.network"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.minidns.hla)
    implementation(libs.minidns.android21)
}
