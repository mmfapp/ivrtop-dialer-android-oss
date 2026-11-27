plugins {
    alias(libs.plugins.ivrtop.android.library)
    alias(libs.plugins.ivrtop.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ivrtop.dialer.core.datastore"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
}
