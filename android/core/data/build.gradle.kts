plugins {
    alias(libs.plugins.ivrtop.android.library)
    alias(libs.plugins.ivrtop.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ivrtop.dialer.core.data"
}

dependencies {
    api(project(":core:common"))
    api(project(":core:datastore"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    api(project(":core:security"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
}
