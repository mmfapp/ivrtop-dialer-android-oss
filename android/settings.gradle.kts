pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ivrtop-dialer"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

// core
include(":core:common")
include(":core:models")
include(":core:designsystem")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:security")
include(":core:data")

// telephony
include(":telephony:sip-api")
include(":telephony:sip-engine")
include(":telephony:telecom")
include(":telephony:audio")

// features
include(":features:onboarding")
include(":features:dialer")
include(":features:contacts")
include(":features:recents")
include(":features:favorites")
include(":features:accounts")
include(":features:incoming-call")
include(":features:active-call")
include(":features:voicemail")
include(":features:blocking")
include(":features:dnd")
include(":features:settings")
