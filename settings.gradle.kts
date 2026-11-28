pluginManagement {
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
        maven("https://maven.mozilla.org/maven2/") {
            content { includeGroupByRegex("org\\.mozilla.*") }
        }
    }
}

rootProject.name = "SonderWeb"
include(":app")
// SonderCircle: circle anything on the screen to search it (its own small app, same releases).
include(":circle")
