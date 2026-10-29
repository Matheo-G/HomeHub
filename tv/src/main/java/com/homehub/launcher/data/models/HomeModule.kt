package com.homehub.launcher.data.models

/**
 * Bloc vertical de l'écran d'accueil.
 *
 * L'accueil n'est pas codé en dur : c'est une liste ordonnée de modules.
 * Pour ajouter un widget (météo, agenda Google, lecture Deezer/Spotify,
 * tableau de bord domotique, assistant IA…) :
 *  1. déclarer ici un nouveau sous-type ;
 *  2. exposer son état depuis un repository et un ViewModel ;
 *  3. ajouter son rendu dans le `when` de `HomeScreen` — le compilateur
 *     signale tout module oublié.
 *
 * À terme, l'ordre et la visibilité des modules pourront être choisis par
 * l'utilisateur et synchronisés dans le cloud.
 */
sealed interface HomeModule {

    /** Clé stable utilisée par la liste paresseuse de l'accueil. */
    val key: String

    /** Grille des applications favorites. */
    data object Favorites : HomeModule {
        override val key: String = "module:favorites"
    }

    /** Rangée horizontale des applications récemment utilisées. */
    data object RecentApps : HomeModule {
        override val key: String = "module:recents"
    }

    companion object {
        /** Disposition par défaut de la V1. */
        val Default: List<HomeModule> = listOf(Favorites, RecentApps)
    }
}
