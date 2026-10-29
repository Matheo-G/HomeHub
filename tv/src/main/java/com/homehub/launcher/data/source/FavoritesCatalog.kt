package com.homehub.launcher.data.source

import com.homehub.launcher.data.models.HomeTile

/**
 * Définition des favoris de l'accueil.
 *
 * Une même application existe souvent en plusieurs paquets selon l'appareil
 * (version Android TV, Google TV ou mobile). Chaque favori liste ses variantes
 * par ordre de préférence : la première installée est utilisée, la première de
 * la liste est proposée au Play Store si aucune n'est présente.
 *
 * Cette liste est la future cible des favoris éditables (ajout, retrait,
 * réordonnancement) : il suffira de la remplacer par une source persistée.
 */
object FavoritesCatalog {

    /** Favori défini par une clé, un titre et ses paquets candidats. */
    private data class Favorite(
        val key: String,
        val title: String,
        val packages: List<String>,
    )

    private val favorites: List<Favorite> = listOf(
        Favorite(
            key = "fav:netflix",
            title = "Netflix",
            packages = listOf("com.netflix.ninja", "com.netflix.mediaclient"),
        ),
        Favorite(
            key = "fav:youtube",
            title = "YouTube",
            packages = listOf(
                "com.google.android.youtube.tv",
                "com.google.android.youtube.googletv",
                "com.google.android.youtube",
            ),
        ),
        Favorite(
            key = "fav:deezer",
            title = "Deezer",
            packages = listOf("deezer.android.tv", "deezer.android.app"),
        ),
        Favorite(
            key = "fav:plex",
            title = "Plex",
            packages = listOf("com.plexapp.android"),
        ),
        Favorite(
            key = "fav:prime",
            title = "Prime Video",
            packages = listOf(
                "com.amazon.amazonvideo.livingroom",
                "com.amazon.avod.thirdpartyclient",
                "com.amazon.avod",
            ),
        ),
    )

    /** Ensemble de tous les paquets connus, utile pour les exclure des récents si besoin. */
    val knownPackages: Set<String> = favorites.flatMapTo(mutableSetOf()) { it.packages }

    /**
     * Construit les tuiles de l'accueil à partir des paquets installés.
     * Les tuiles système (Paramètres, Toutes les applications) terminent la grille.
     */
    fun resolve(installedPackages: Set<String>): List<HomeTile> {
        val apps = favorites.map { favorite ->
            HomeTile.App(
                key = favorite.key,
                title = favorite.title,
                packageName = favorite.packages.firstOrNull { it in installedPackages },
                installTarget = favorite.packages.first(),
            )
        }
        return apps + HomeTile.Settings + HomeTile.AllApps
    }
}
