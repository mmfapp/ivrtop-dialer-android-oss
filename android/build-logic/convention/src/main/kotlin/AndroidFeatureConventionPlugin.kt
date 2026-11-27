import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Feature modules: UI + ViewModels. They may depend on telephony:sip-api but NEVER on sip-engine. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("ivrtop.android.library")
        pluginManager.apply("ivrtop.android.compose")
        pluginManager.apply("ivrtop.android.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        dependencies {
            add("implementation", project(":core:common"))
            add("implementation", project(":core:models"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:data"))
            add("implementation", project(":telephony:sip-api"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-navigation-compose"))
            add("implementation", libs.lib("androidx-hilt-navigation-compose"))
            add("implementation", libs.lib("kotlinx-serialization-json"))
        }
        configurations.configureEach {
            // Architectural guard: UI layers can never see the PJSIP implementation.
            dependencies.whenObjectAdded {
                if (this is org.gradle.api.artifacts.ProjectDependency && this.path == ":telephony:sip-engine") {
                    error("Feature module ${target.path} must not depend on :telephony:sip-engine")
                }
            }
        }
    }
}
