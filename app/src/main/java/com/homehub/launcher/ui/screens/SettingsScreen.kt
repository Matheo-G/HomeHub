package com.homehub.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.homehub.launcher.BuildConfig
import com.homehub.launcher.data.models.AccentColor
import com.homehub.launcher.data.models.CardSize
import com.homehub.launcher.data.models.LauncherSettings
import com.homehub.launcher.data.models.WallpaperStyle
import com.homehub.launcher.ui.components.ArrowOutGlyph
import com.homehub.launcher.ui.components.CheckGlyph
import com.homehub.launcher.ui.components.FocusableSurface
import com.homehub.launcher.ui.components.SectionTitle
import com.homehub.launcher.ui.components.awaitLayout
import com.homehub.launcher.ui.components.drawStaticWallpaper
import com.homehub.launcher.ui.theme.HubColors
import com.homehub.launcher.ui.theme.HubDimens
import com.homehub.launcher.ui.theme.LocalAccent
import com.homehub.launcher.ui.theme.toColor
import com.homehub.launcher.ui.theme.wallpaperPalette
import com.homehub.launcher.viewmodel.SettingsViewModel

/**
 * Page de personnalisation.
 *
 * Chaque choix s'applique immédiatement et en douceur à toute l'interface
 * (y compris au fond animé visible derrière cette page), puis est enregistré
 * localement.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    settings: LauncherSettings,
    modifier: Modifier = Modifier,
) {
    val usageAccessGranted by viewModel.usageAccessGranted.collectAsStateWithLifecycle()
    val initialFocus = remember { FocusRequester() }

    // L'utilisateur revient peut-être de l'écran système d'autorisation.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSystemState() }

    LaunchedEffect(Unit) {
        awaitLayout()
        runCatching { initialFocus.requestFocus() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = HubDimens.ScreenHorizontal,
            end = HubDimens.ScreenHorizontal,
            top = HubDimens.ScreenTop,
            bottom = 56.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        item(key = "header") {
            Column {
                Text(
                    text = "Paramètres",
                    style = MaterialTheme.typography.headlineMedium,
                    color = HubColors.TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Personnalisez votre écran d'accueil. Les changements s'appliquent immédiatement.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HubColors.TextTertiary,
                )
            }
        }

        item(key = "accent") {
            AccentSection(
                selected = settings.accent,
                onSelect = viewModel::setAccent,
                focusRequester = initialFocus,
            )
        }

        item(key = "wallpaper") {
            SettingsSection(title = "Fond d'écran") {
                val accent = LocalAccent.current.value
                WallpaperStyle.entries.forEach { style ->
                    ChoiceCard(
                        title = style.label,
                        subtitle = style.description,
                        selected = style == settings.wallpaper,
                        onClick = { viewModel.setWallpaper(style) },
                        preview = { drawStaticWallpaper(wallpaperPalette(style, accent)) },
                    )
                }
            }
        }

        item(key = "cardSize") {
            SettingsSection(title = "Taille des cartes") {
                val accent = LocalAccent.current.value
                CardSize.entries.forEach { option ->
                    ChoiceCard(
                        title = option.label,
                        subtitle = "${(option.scale * 100).toInt()} %",
                        selected = option == settings.cardSize,
                        onClick = { viewModel.setCardSize(option) },
                        preview = { drawCardSizePreview(option.scale, accent) },
                    )
                }
            }
        }

        item(key = "system") {
            SettingsSection(title = "Système") {
                ActionCard(
                    title = "Paramètres Android TV",
                    subtitle = "Réseau, son, comptes, affichage",
                    onClick = viewModel::openSystemSettings,
                )
                ActionCard(
                    title = "Historique d'utilisation",
                    subtitle = if (usageAccessGranted) {
                        "Autorisé : toutes vos applications apparaissent dans les récents"
                    } else {
                        "Autoriser pour voir aussi les applications ouvertes hors de HomeHub"
                    },
                    onClick = viewModel::openUsageAccessSettings,
                )
            }
        }

        item(key = "about") {
            Text(
                text = "HomeHub ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = HubColors.TextTertiary,
            )
        }
    }
}

/** Titre de section + rangée d'options, avec la marge nécessaire au focus. */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        SectionTitle(title)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            content()
        }
    }
}

/** Pastilles de couleur d'accent, avec le nom de la teinte focalisée. */
@Composable
private fun AccentSection(
    selected: AccentColor,
    onSelect: (AccentColor) -> Unit,
    focusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf<AccentColor?>(null) }
    Column {
        SectionTitle("Couleur d'accent")
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            AccentColor.entries.forEach { accent ->
                val isSelected = accent == selected
                FocusableSurface(
                    onClick = { onSelect(accent) },
                    modifier = Modifier.size(SwatchSize),
                    cornerRadius = SwatchSize / 2,
                    containerColor = accent.toColor(),
                    focusedScale = 1.18f,
                    focusRequester = if (isSelected) focusRequester else null,
                    onFocusChange = { hasFocus ->
                        if (hasFocus) focused = accent else if (focused == accent) focused = null
                    },
                ) {
                    if (isSelected) CheckGlyph(Color.White, Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = (focused ?: selected).label,
            style = MaterialTheme.typography.titleSmall,
            color = HubColors.TextSecondary,
        )
    }
}

/** Option illustrée (fond d'écran, taille de carte). */
@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    preview: DrawScope.() -> Unit,
) {
    Column(modifier = Modifier.width(ChoiceWidth)) {
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier.size(ChoiceWidth, ChoiceWidth * (9f / 16f)),
            cornerRadius = 18.dp,
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind(preview),
            )
            if (selected) {
                SelectedBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) HubColors.TextPrimary else HubColors.TextSecondary,
            maxLines = 1,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = HubColors.TextTertiary,
            maxLines = 2,
        )
    }
}

/** Action ouvrant un écran système. */
@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.size(width = 330.dp, height = 82.dp),
        cornerRadius = 18.dp,
        focusedScale = 1.06f,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = HubColors.TextPrimary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = HubColors.TextTertiary,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(12.dp))
            ArrowOutGlyph(HubColors.TextSecondary, Modifier.size(18.dp))
        }
    }
}

/** Pastille « sélectionné » aux couleurs de l'accent. */
@Composable
private fun SelectedBadge(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.value
    Box(
        modifier = modifier
            .size(24.dp)
            .background(accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        CheckGlyph(Color.White, Modifier.size(14.dp))
    }
}

/** Aperçu d'une taille de carte : trois cartes miniatures à l'échelle. */
private fun DrawScope.drawCardSizePreview(scale: Float, accent: Color) {
    drawRect(HubColors.CardRaised)
    val cardWidth = size.width * 0.22f * scale
    val cardHeight = cardWidth * (9f / 16f)
    val gap = size.width * 0.04f
    val total = cardWidth * 3 + gap * 2
    val startX = (size.width - total) / 2f
    val top = (size.height - cardHeight) / 2f
    for (index in 0 until 3) {
        drawRoundRect(
            color = if (index == 1) accent else Color.White.copy(alpha = 0.22f),
            topLeft = Offset(startX + index * (cardWidth + gap), top),
            size = Size(cardWidth, cardHeight),
            cornerRadius = CornerRadius(cardHeight * 0.16f),
        )
    }
}

private val SwatchSize: Dp = 52.dp
private val ChoiceWidth: Dp = 196.dp
