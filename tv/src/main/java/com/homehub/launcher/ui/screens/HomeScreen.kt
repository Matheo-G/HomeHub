package com.homehub.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.homehub.launcher.data.models.AppInfo
import com.homehub.launcher.data.models.HomeModule
import com.homehub.launcher.data.models.HomeTile
import com.homehub.launcher.ui.components.AppCard
import com.homehub.launcher.ui.components.FocusRegistry
import com.homehub.launcher.ui.components.HomeTileCard
import com.homehub.launcher.ui.components.LabelBlockHeight
import com.homehub.launcher.ui.components.SectionTitle
import com.homehub.launcher.ui.components.StatusBar
import com.homehub.launcher.ui.components.awaitLayout
import com.homehub.launcher.ui.components.rememberFocusRegistry
import com.homehub.launcher.ui.theme.HubColors
import com.homehub.launcher.ui.theme.HubDimens
import com.homehub.launcher.ui.theme.LocalCardScale
import com.homehub.launcher.viewmodel.HomeViewModel

/**
 * Écran d'accueil : barre d'état, puis les modules dans l'ordre défini par
 * [HomeViewModel.modules] (favoris, applications récentes, et demain les widgets).
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    userName: String,
    onOpenAllApps: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recents by viewModel.recentApps.collectAsStateWithLifecycle()
    val focusRegistry = rememberFocusRegistry()

    val onTileClick: (HomeTile) -> Unit = { tile ->
        when (tile) {
            is HomeTile.App -> {
                val packageName = tile.packageName
                if (packageName != null) viewModel.openApp(packageName) else viewModel.openStore(tile.installTarget)
            }
            HomeTile.Settings -> onOpenSettings()
            HomeTile.AllApps -> onOpenAllApps()
        }
    }
    val rememberFocus: (String) -> Unit = { key -> viewModel.lastFocusedKey = key }

    // Focus initial : la dernière carte utilisée, sinon le premier favori.
    val favoritesReady = favorites != null
    LaunchedEffect(favoritesReady) {
        if (!favoritesReady) return@LaunchedEffect
        awaitLayout()
        val restored = viewModel.lastFocusedKey?.let(focusRegistry::tryFocus) ?: false
        if (!restored) {
            viewModel.favorites.value?.firstOrNull()?.let { focusRegistry.tryFocus(it.key) }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        StatusBar(
            userName = userName,
            connectionStatus = viewModel.connectionStatus,
            modifier = Modifier.padding(
                start = HubDimens.ScreenHorizontal,
                end = HubDimens.ScreenHorizontal,
                top = HubDimens.ScreenTop,
            ),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(HubDimens.SectionGap),
        ) {
            for (module in viewModel.modules) {
                item(key = module.key, contentType = module.key) {
                    when (module) {
                        HomeModule.Favorites -> FavoritesSection(
                            tiles = favorites,
                            focusRegistry = focusRegistry,
                            onTileClick = onTileClick,
                            onFocused = rememberFocus,
                        )
                        HomeModule.RecentApps -> RecentAppsSection(
                            apps = recents,
                            focusRegistry = focusRegistry,
                            onAppClick = { app -> viewModel.openApp(app.packageName) },
                            onFocused = rememberFocus,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Grille des favoris. Le nombre de colonnes découle de la largeur disponible
 * et de la taille de carte choisie : la grille reste équilibrée quel que soit
 * le réglage.
 */
@Composable
private fun FavoritesSection(
    tiles: List<HomeTile>?,
    focusRegistry: FocusRegistry,
    onTileClick: (HomeTile) -> Unit,
    onFocused: (String) -> Unit,
) {
    val cardScale = LocalCardScale.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HubDimens.ScreenHorizontal),
    ) {
        val cardWidth = HubDimens.FavoriteWidth * cardScale
        val gap = HubDimens.FavoriteGap
        val columns = ((maxWidth + gap) / (cardWidth + gap)).toInt().coerceAtLeast(1)

        Column {
            SectionTitle("Favoris")
            Spacer(Modifier.height(16.dp))
            if (tiles == null) {
                // Réserve la place pendant le tout premier chargement (quelques millisecondes).
                Spacer(Modifier.height(cardWidth * (9f / 16f) + LabelBlockHeight))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    tiles.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            row.forEach { tile ->
                                key(tile.key) {
                                    HomeTileCard(
                                        tile = tile,
                                        width = cardWidth,
                                        onClick = { onTileClick(tile) },
                                        focusRequester = focusRegistry[tile.key],
                                        onFocused = { onFocused(tile.key) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Rangée horizontale des applications récentes. */
@Composable
private fun RecentAppsSection(
    apps: List<AppInfo>,
    focusRegistry: FocusRegistry,
    onAppClick: (AppInfo) -> Unit,
    onFocused: (String) -> Unit,
) {
    val cardWidth = HubDimens.RecentWidth * LocalCardScale.current
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(
            text = "Applications récentes",
            modifier = Modifier.padding(horizontal = HubDimens.ScreenHorizontal),
        )
        if (apps.isEmpty()) {
            Text(
                text = "Les applications que vous ouvrez apparaîtront ici.",
                style = MaterialTheme.typography.bodyMedium,
                color = HubColors.TextTertiary,
                modifier = Modifier.padding(
                    start = HubDimens.ScreenHorizontal,
                    end = HubDimens.ScreenHorizontal,
                    top = 14.dp,
                ),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(
                    horizontal = HubDimens.ScreenHorizontal,
                    vertical = HubDimens.FocusBleed,
                ),
                horizontalArrangement = Arrangement.spacedBy(HubDimens.RecentGap),
            ) {
                items(items = apps, key = { recentKey(it) }) { app ->
                    val key = recentKey(app)
                    AppCard(
                        app = app,
                        onClick = { onAppClick(app) },
                        cardModifier = Modifier.size(cardWidth, cardWidth * (9f / 16f)),
                        modifier = Modifier.width(cardWidth),
                        cornerRadius = HubDimens.RecentCorner,
                        focusRequester = focusRegistry[key],
                        onFocused = { onFocused(key) },
                    )
                }
            }
        }
    }
}

private fun recentKey(app: AppInfo): String = "recent:${app.packageName}"
