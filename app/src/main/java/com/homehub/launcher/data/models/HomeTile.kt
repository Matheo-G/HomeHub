package com.homehub.launcher.data.models

import androidx.compose.runtime.Immutable

/**
 * Tuile affichée dans la grille de favoris de l'accueil.
 *
 * Interface scellée : ajouter un nouveau type de tuile (scène domotique,
 * partage NAS, session Steam Link…) oblige le compilateur à signaler chaque
 * `when` qui ne le gère pas encore.
 */
@Immutable
sealed interface HomeTile {

    /** Clé stable, utilisée par les listes Compose et la restauration du focus. */
    val key: String

    /** Titre affiché sous la carte. */
    val title: String

    /**
     * Application favorite.
     *
     * @property packageName paquet effectivement installé, ou `null` si aucune
     *                       des variantes connues n'est présente.
     * @property installTarget paquet proposé au Play Store quand l'application manque.
     */
    @Immutable
    data class App(
        override val key: String,
        override val title: String,
        val packageName: String?,
        val installTarget: String,
    ) : HomeTile {
        /** Vrai si l'application peut être ouverte directement. */
        val isInstalled: Boolean get() = packageName != null
    }

    /** Ouvre la grille de toutes les applications. */
    data object AllApps : HomeTile {
        override val key: String = "builtin:all_apps"
        override val title: String = "Toutes les applications"
    }

    /** Ouvre la page de personnalisation de HomeHub. */
    data object Settings : HomeTile {
        override val key: String = "builtin:settings"
        override val title: String = "Paramètres"
    }
}
