/*
 * HomeHub — configuration des dépôts et des modules.
 *
 * Découpage et règles de dépendance : docs/adr/0009-modularisation.md.
 * La TV (:tv) ne dépend jamais d'un module :phone:* ; :core:* et
 * :phone:intent sont du Kotlin pur, testables sans Android.
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
include(":tv")             // HomeHub TV : launcher Android TV (affiche et exécute)
include(":phone")          // HomeHub Remote : application téléphone (le cerveau)
include(":phone:intent")   // Moteur d'intentions, Kotlin pur
include(":core:protocol")  // Protocole local v2 (JSON-RPC 2.0), Kotlin pur
