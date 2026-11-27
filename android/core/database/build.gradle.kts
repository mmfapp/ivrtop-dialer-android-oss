plugins {
    alias(libs.plugins.ivrtop.android.library)
    alias(libs.plugins.ivrtop.android.hilt)
    alias(libs.plugins.ivrtop.android.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ivrtop.dialer.core.database"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
