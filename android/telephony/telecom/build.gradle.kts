plugins {
    alias(libs.plugins.ivrtop.android.library)
    alias(libs.plugins.ivrtop.android.hilt)
}

android {
    namespace = "com.ivrtop.dialer.telephony.telecom"
}

dependencies {
    api(project(":telephony:sip-api"))
    implementation(project(":telephony:audio"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(project(":core:datastore"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.telecom)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
