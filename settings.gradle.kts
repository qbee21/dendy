pluginManagement {
    repositories {
        google()
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

rootProject.name = "Dendy2026"

include(
    ":app",
    ":core-model",
    ":core-data",
    ":core-emulation",
    ":feature-library",
    ":feature-player",
    ":feature-settings",
)

