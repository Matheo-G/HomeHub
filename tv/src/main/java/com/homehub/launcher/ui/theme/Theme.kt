package com.homehub.launcher.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.homehub.launcher.data.models.LauncherSettings

/**
 * Couleur d'accent animée.
 *
 * Fournie sous forme de [State] et non de valeur : les composants la lisent
 * dans leurs phases de dessin (`drawBehind`, `drawWithContent`), si bien qu'un
 * changement d'accent anime les halos et contours sans recomposer l'arbre.
 */
val LocalAccent = staticCompositionLocalOf<State<Color>> {
    mutableStateOf(Color(0xFF3D7BFF))
}

/** Facteur d'échelle des cartes choisi par l'utilisateur (animé lors d'un changement). */
val LocalCardScale = compositionLocalOf { 1f }

/**
 * Thème racine : Material 3 pour TV + jetons HomeHub.
 */
@Composable
fun HomeHubTheme(
    settings: LauncherSettings,
    content: @Composable () -> Unit,
) {
    val accentTarget = settings.accent.toColor()
    val accent = animateColorAsState(accentTarget, HubMotion.AccentChange, label = "accent")
    val cardScale = animateFloatAsState(settings.cardSize.scale, HubMotion.SizeSpring, label = "cardScale")

    val colorScheme = remember(accentTarget) {
        darkColorScheme(
            primary = accentTarget,
            onPrimary = Color.White,
            background = HubColors.Background,
            onBackground = HubColors.TextPrimary,
            surface = HubColors.Card,
            onSurface = HubColors.TextPrimary,
            surfaceVariant = HubColors.CardRaised,
            onSurfaceVariant = HubColors.TextSecondary,
        )
    }

    MaterialTheme(colorScheme = colorScheme, typography = HubTypography) {
        CompositionLocalProvider(
            LocalAccent provides accent,
            LocalCardScale provides cardScale.value,
            content = content,
        )
    }
}
