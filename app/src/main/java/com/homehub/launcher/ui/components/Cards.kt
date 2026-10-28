package com.homehub.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.homehub.launcher.data.models.AppInfo
import com.homehub.launcher.data.models.HomeTile
import com.homehub.launcher.ui.theme.HubColors
import com.homehub.launcher.ui.theme.HubMotion
import com.homehub.launcher.ui.theme.LocalAccent

/**
 * Carte focalisable surmontant un titre.
 * Le titre s'éclaire quand la carte prend le focus (animation lue au dessin).
 *
 * @param cardModifier taille ou ratio de la surface (le titre se place dessous).
 */
@Composable
fun LabeledCard(
    title: String,
    onClick: () -> Unit,
    cardModifier: Modifier,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    titleStyle: TextStyle = MaterialTheme.typography.titleSmall,
    focusRequester: FocusRequester? = null,
    onFocusChange: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val titleAlpha = animateFloatAsState(
        targetValue = if (focused) 1f else 0.6f,
        animationSpec = HubMotion.Fade,
        label = "titleAlpha",
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.Top) {
        FocusableSurface(
            onClick = onClick,
            modifier = cardModifier,
            cornerRadius = cornerRadius,
            focusRequester = focusRequester,
            onFocusChange = { isFocused ->
                focused = isFocused
                onFocusChange(isFocused)
            },
            content = content,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = titleStyle,
            color = HubColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .graphicsLayer { alpha = titleAlpha.value },
        )
    }
}

/**
 * Carte de la grille de favoris : application, Paramètres ou Toutes les applications.
 */
@Composable
fun HomeTileCard(
    tile: HomeTile,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
) {
    val height = width * (9f / 16f)
    LabeledCard(
        title = tile.title,
        onClick = onClick,
        cardModifier = Modifier.size(width, height),
        modifier = modifier.size(width = width, height = height + LabelBlockHeight),
        focusRequester = focusRequester,
        onFocusChange = { if (it) onFocused() },
    ) {
        when (tile) {
            is HomeTile.App ->
                if (tile.isInstalled) {
                    AppArtwork(packageName = tile.packageName, title = tile.title, modifier = Modifier.fillMaxSize())
                } else {
                    InstallInvite(title = tile.title)
                }
            HomeTile.Settings -> BuiltinTileContent { color, glyphModifier -> GearGlyph(color, glyphModifier) }
            HomeTile.AllApps -> BuiltinTileContent { color, glyphModifier -> GridGlyph(color, glyphModifier) }
        }
    }
}

/**
 * Carte d'une application (récents, grille complète).
 * La surface occupe toute la largeur disponible au ratio 16:9 des bannières TV.
 */
@Composable
fun AppCard(
    app: AppInfo,
    onClick: () -> Unit,
    cardModifier: Modifier,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
) {
    LabeledCard(
        title = app.label,
        onClick = onClick,
        cardModifier = cardModifier,
        modifier = modifier,
        cornerRadius = cornerRadius,
        titleStyle = MaterialTheme.typography.bodyMedium,
        focusRequester = focusRequester,
        onFocusChange = { if (it) onFocused() },
    ) {
        AppArtwork(packageName = app.packageName, title = app.label, modifier = Modifier.fillMaxSize())
    }
}

/** Hauteur réservée au titre sous une carte (espacement + une ligne). */
val LabelBlockHeight = 34.dp

/** Tuile système : pictogramme centré sur un fond teinté par l'accent. */
@Composable
private fun BuiltinTileContent(glyph: @Composable (Color, Modifier) -> Unit) {
    val accent = LocalAccent.current.value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(accent.copy(alpha = 0.22f), HubColors.CardRaised),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        glyph(HubColors.TextPrimary, Modifier.size(34.dp))
    }
}

/** Favori non installé : invitation à l'installer depuis le Play Store. */
@Composable
private fun InstallInvite(title: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlusGlyph(HubColors.TextTertiary, Modifier.size(22.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Installer $title",
            style = MaterialTheme.typography.bodySmall,
            color = HubColors.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
