import java.util.Properties

plugins {
    alias(libs.plugins.ivrtop.android.application)
    alias(libs.plugins.ivrtop.android.compose)
    alias(libs.plugins.ivrtop.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

val versionProps = Properties().apply {
    val f = rootProject.file("version.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val appVersionCode = (versionProps.getProperty("versionCode") ?: "1").toInt()
val appVersionName = versionProps.getProperty("versionName") ?: "1.0.0"

fun prop(name: String): String = providers.gradleProperty(name).get()

android {
    namespace = "com.ivrtop.dialer"

    defaultConfig {
        applicationId = "com.ivrtop.dialer"
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "APP_LINK_HOST", "\"${prop("ivrtop.appLinkHost")}\"")
        buildConfigField("String", "APP_LINK_PATH", "\"${prop("ivrtop.appLinkPath")}\"")
        buildConfigField("String", "DEFAULT_SIP_DOMAIN", "\"${prop("ivrtop.defaultSipDomain")}\"")
        buildConfigField("String", "LINK_ALLOWED_DOMAINS", "\"${prop("ivrtop.linkAllowedDomains")}\"")
        manifestPlaceholders["appLinkHost"] = prop("ivrtop.appLinkHost")
        manifestPlaceholders["appLinkPath"] = prop("ivrtop.appLinkPath")
    }

    androidResources {
        // Hebrew is the default locale; English is the only other shipped language.
        localeFilters += listOf("he", "iw", "en")
    }

    signingConfigs {
        create("release") {
            // Injected by CI / local env – never committed. See docs/GOOGLE_PLAY_RELEASE.md.
            val ks = System.getenv("IVRTOP_KEYSTORE_FILE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("IVRTOP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("IVRTOP_KEY_ALIAS")
                keyPassword = System.getenv("IVRTOP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    bundle {
        language { enableSplit = false } // in-app language switch needs all locales present
    }

    packaging {
        jniLibs { useLegacyPackaging = false } // 16 KB page-size alignment
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        disable += setOf("MissingTranslation")
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:models"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(project(":telephony:sip-api"))
    implementation(project(":telephony:sip-engine")) // only :app wires the concrete engine
    implementation(project(":telephony:telecom"))
    implementation(project(":telephony:audio"))
    implementation(project(":features:onboarding"))
    implementation(project(":features:dialer"))
    implementation(project(":features:contacts"))
    implementation(project(":features:recents"))
    implementation(project(":features:favorites"))
    implementation(project(":features:accounts"))
    implementation(project(":features:incoming-call"))
    implementation(project(":features:active-call"))
    implementation(project(":features:voicemail"))
    implementation(project(":features:blocking"))
    implementation(project(":features:dnd"))
    implementation(project(":features:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
