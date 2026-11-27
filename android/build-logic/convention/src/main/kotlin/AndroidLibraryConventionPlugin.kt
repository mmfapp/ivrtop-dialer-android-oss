import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            compileSdk = IvrtopSdk.COMPILE
            defaultConfig {
                minSdk = IvrtopSdk.MIN
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                if (target.file("consumer-rules.pro").exists()) consumerProguardFiles("consumer-rules.pro")
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            testOptions {
                unitTests.isIncludeAndroidResources = true
                unitTests.isReturnDefaultValues = true
            }
        }
        configureKotlin()
        // Many modules have no unit tests yet; Gradle 9 would fail their (empty) test tasks.
        tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }
        dependencies {
            add("implementation", libs.lib("kotlinx-coroutines-android"))
            add("implementation", libs.lib("timber"))
            add("testImplementation", libs.lib("junit"))
            add("testImplementation", libs.lib("truth"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
            add("testImplementation", libs.lib("turbine"))
            add("testImplementation", libs.lib("mockk"))
            add("androidTestImplementation", libs.lib("androidx-test-ext-junit"))
            add("androidTestImplementation", libs.lib("androidx-test-runner"))
        }
    }
}
