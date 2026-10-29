package com.homehub.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.homehub.launcher.ui.components.AppCard
import com.homehub.launcher.ui.components.awaitLayout
import com.homehub.launcher.ui.theme.HubColors
import com.homehub.launcher.ui.theme.HubDimens
import com.homehub.launcher.ui.theme.LocalCardScale
import com.homehub.launcher.viewmodel.HomeViewModel

/**
 * Grille alphabétique de toutes les applications installées.
 * Mise à jour en direct à chaque installation ou désinstallation.
 */
@Composable
fun AllAppsScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val apps by viewModel.allApps.collectAsStateWithLifecycle()
    val firstCard = remember { FocusRequester() }
    val hasApps = apps?.isNotEmpty() == true

    LaunchedEffect(hasApps) {
        if (hasApps) {
            awaitLayout()
            runCatching { firstCard.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = HubDimens.ScreenTop),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = HubDimens.ScreenHorizontal),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "Toutes les applications",
                style = MaterialTheme.typography.headlineMedium,
                color = HubColors.TextPrimary,
            )
            Spacer(Modifier.width(14.dp))
            apps?.let { list ->
                Text(
                    text = if (list.size > 1) "${list.size} applications" else "${list.size} application",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HubColors.TextTertiary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }

        val list = apps.orEmpty()
        if (apps != null && list.isEmpty()) {
            Text(
                text = "Aucune application lançable n'a été trouvée.",
                style = MaterialTheme.typography.bodyMedium,
                color = HubColors.TextSecondary,
                modifier = Modifier.padding(horizontal = HubDimens.ScreenHorizontal, vertical = 24.dp),
            )
        } else if (list.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp * LocalCardScale.current),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = HubDimens.ScreenHorizontal,
                    end = HubDimens.ScreenHorizontal,
                    top = HubDimens.FocusBleed + 8.dp,
                    bottom = 48.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                itemsIndexed(items = list, key = { _, app -> app.packageName }) { index, app ->
                    AppCard(
                        app = app,
                        onClick = { viewModel.openApp(app.packageName) },
                        cardModifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(HubDimens.BannerRatio),
                        focusRequester = if (index == 0) firstCard else null,
                    )
                }
            }
        }
    }
}
