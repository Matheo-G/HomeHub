package com.homehub.launcher.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color

/**
 * Langage de mouvement HomeHub.
 *
 * Le focus utilise un ressort légèrement sous-amorti : la carte « respire »
 * en arrivant à sa taille, sans rebond visible. Les fondus restent courts
 * pour que la télécommande ne paraisse jamais en retard.
 */
object HubMotion {
    /** Agrandissement d'une carte focalisée. */
    const val FocusScale = 1.10f

    /** Écrasement léger à l'appui sur OK. */
    const val PressScale = 0.965f

    val FocusSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 340f)
    val PressSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    val SizeSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)

    val Fade = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)
    val AccentChange = tween<Color>(durationMillis = 600, easing = FastOutSlowInEasing)

    const val ScreenEnterMillis = 360
    const val ScreenExitMillis = 220
    const val WallpaperCrossfadeMillis = 900

    /** Durée d'une boucle complète du fond animé (boucle parfaitement raccordée). */
    const val WallpaperLoopMillis = 120_000
}
