package com.homehub.launcher.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.homehub.launcher.data.models.AccentColor
import com.homehub.launcher.data.models.WallpaperStyle

/**
 * Palette HomeHub : fond presque noir, cartes gris foncé, texte blanc.
 * L'accent n'est pas ici : il est choisi par l'utilisateur ([AccentColor]).
 */
object HubColors {
    val Background = Color(0xFF07090E)
    val Card = Color(0xFF161A22)
    val CardRaised = Color(0xFF1E232E)
    val CardOutline = Color(0x14FFFFFF)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xB8FFFFFF)
    val TextTertiary = Color(0x70FFFFFF)
}

/** Conversion de l'accent enregistré (ARGB) en couleur Compose. */
fun AccentColor.toColor(): Color = Color(argb)

/**
 * Couleurs d'un fond d'écran.
 *
 * @property top haut du dégradé de base.
 * @property bottom bas du dégradé de base.
 * @property glowPrimary lumière principale (teintée par l'accent).
 * @property glowSecondary lumière d'appoint.
 * @property particle couleur des particules.
 */
@Immutable
data class WallpaperPalette(
    val top: Color,
    val bottom: Color,
    val glowPrimary: Color,
    val glowSecondary: Color,
    val particle: Color,
)

/** Palette d'un style de fond, harmonisée avec l'accent courant. */
fun wallpaperPalette(style: WallpaperStyle, accent: Color): WallpaperPalette = when (style) {
    WallpaperStyle.AURORA -> WallpaperPalette(
        top = Color(0xFF0C111D),
        bottom = Color(0xFF05070B),
        glowPrimary = accent.copy(alpha = 0.22f),
        glowSecondary = Color(0xFF14B8A6).copy(alpha = 0.10f),
        particle = lerp(Color.White, accent, 0.35f),
    )
    WallpaperStyle.NEBULA -> WallpaperPalette(
        top = Color(0xFF120B1F),
        bottom = Color(0xFF050409),
        glowPrimary = Color(0xFF8B5CF6).copy(alpha = 0.20f),
        glowSecondary = lerp(Color(0xFFEC4899), accent, 0.5f).copy(alpha = 0.12f),
        particle = Color(0xFFE9D5FF),
    )
    WallpaperStyle.MIDNIGHT -> WallpaperPalette(
        top = Color(0xFF0A0C11),
        bottom = Color(0xFF040507),
        glowPrimary = accent.copy(alpha = 0.10f),
        glowSecondary = Color.Transparent,
        particle = Color.Transparent,
    )
}
