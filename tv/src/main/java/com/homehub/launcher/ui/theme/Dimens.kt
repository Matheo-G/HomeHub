package com.homehub.launcher.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Dimensions de référence (Android TV affiche en 960 x 540 dp).
 * Les tailles de cartes sont multipliées par le facteur choisi dans les Paramètres.
 */
object HubDimens {
    /** Marge latérale de sécurité (overscan des téléviseurs). */
    val ScreenHorizontal = 56.dp
    val ScreenTop = 28.dp

    /** Carte favorite : 16:9, largeur avant mise à l'échelle utilisateur. */
    val FavoriteWidth = 184.dp
    val FavoriteCorner = 22.dp
    val FavoriteGap = 20.dp

    /** Carte de la rangée « Applications récentes ». */
    val RecentWidth = 140.dp
    val RecentCorner = 16.dp
    val RecentGap = 18.dp

    /** Espace vertical entre deux blocs de l'accueil. */
    val SectionGap = 24.dp

    /** Marge laissée autour des rangées pour que halo et agrandissement ne soient jamais rognés. */
    val FocusBleed = 20.dp

    /** Rapport largeur / hauteur des bannières Android TV. */
    const val BannerRatio = 16f / 9f
}
