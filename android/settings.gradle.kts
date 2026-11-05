pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://zello-sdk.s3.amazonaws.com/android/latest")
            content { includeGroup("com.zello") }
        }
    }
}
rootProject.name = "NepalScoutsRescuer"
include(":app")
