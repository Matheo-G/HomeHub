/*
 * HomeHub — configuration des dépôts et des modules.
 *
 * Modules : :app (launcher TV), :remote (télécommande téléphone) et
 * :protocol (messages échangés entre les deux). Les futures briques
 * (widgets, intégrations domotique, IA…) pourront devenir des modules
 * Gradle indépendants (:feature:weather, :integration:nas…) sans toucher
 * au reste de l'arborescence.
 */
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
    // Interdit les dépôts déclarés dans les modules : tout passe par ici.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HomeHub"
include(":app")      // Launcher Android TV
include(":remote")   // Télécommande pour téléphone Android
include(":protocol") // Protocole partagé TV ↔ téléphone
