package com.homehub.launcher.data.models

import androidx.compose.runtime.Immutable

/**
 * Couleur d'accent proposée à l'utilisateur.
 *
 * La valeur est stockée en ARGB brut pour que la couche données reste
 * indépendante de Compose ; la conversion en `Color` se fait dans l'UI.
 */
enum class AccentColor(val label: String, val argb: Long) {
    ELECTRIC_BLUE("Bleu électrique", 0xFF3D7BFF),
    CYAN("Cyan", 0xFF22D3EE),
    VIOLET("Violet", 0xFF8B5CF6),
    EMERALD("Émeraude", 0xFF10B981),
    AMBER("Ambre", 0xFFF5A524),
    CORAL("Corail", 0xFFFF5D6C),
}

/** Fond d'écran de l'accueil. Ajouter un style = ajouter une palette dans `wallpaperPalette()`. */
enum class WallpaperStyle(val label: String, val description: String, val isAnimated: Boolean) {
    AURORA("Aurore", "Lumières douces et particules", true),
    NEBULA("Nébuleuse", "Teintes violettes en mouvement", true),
    MIDNIGHT("Minuit", "Fond statique, sobriété totale", false),
}

/** Taille des cartes de l'accueil, exprimée comme un facteur d'échelle. */
enum class CardSize(val label: String, val scale: Float) {
    COMPACT("Compacte", 0.86f),
    STANDARD("Standard", 1f),
    LARGE("Grande", 1.16f),
}

/**
 * Réglages persistants du launcher.
 *
 * @property userName prénom affiché dans la salutation de l'accueil.
 */
@Immutable
data class LauncherSettings(
    val accent: AccentColor = AccentColor.ELECTRIC_BLUE,
    val wallpaper: WallpaperStyle = WallpaperStyle.AURORA,
    val cardSize: CardSize = CardSize.STANDARD,
    val userName: String = DEFAULT_USER_NAME,
) {
    companion object {
        const val DEFAULT_USER_NAME: String = "Mathéo"
    }
}
