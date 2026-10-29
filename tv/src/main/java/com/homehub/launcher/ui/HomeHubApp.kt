package com.homehub.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.homehub.launcher.cards.CardsStore
import com.homehub.launcher.cards.DisplayedSurface
import com.homehub.launcher.data.models.LauncherSettings
import com.homehub.launcher.ui.cards.CardsOverlay
import com.homehub.protocol.DismissReason
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homehub.launcher.ui.components.AnimatedBackground
import com.homehub.launcher.ui.components.ArtworkLoader
import com.homehub.launcher.ui.components.LocalArtworkLoader
import com.homehub.launcher.ui.components.RemoteFeedbackBanner
import com.homehub.launcher.remote.RemoteFeedback
import kotlinx.coroutines.flow.Flow
import com.homehub.launcher.ui.navigation.Destination
import com.homehub.launcher.ui.navigation.Navigator
import com.homehub.launcher.ui.navigation.screenTransition
import com.homehub.launcher.ui.screens.AllAppsScreen
import com.homehub.launcher.ui.screens.HomeScreen
import com.homehub.launcher.ui.screens.SettingsScreen
import com.homehub.launcher.ui.theme.HomeHubTheme
import com.homehub.launcher.ui.theme.HubColors
import com.homehub.launcher.viewmodel.HomeViewModel
import com.homehub.launcher.viewmodel.SettingsViewModel

/**
 * Racine de l'interface : thème, fond animé commun à tous les écrans,
 * navigation avec transitions.
 *
 * Le fond est placé sous la navigation : il ne s'interrompt jamais pendant un
 * changement d'écran, ce qui renforce l'impression d'un espace continu.
 */
@Composable
fun HomeHubApp(
    navigator: Navigator,
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    artworkLoader: ArtworkLoader,
    remoteFeedback: Flow<RemoteFeedback>,
    cardsStore: CardsStore,
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val cards by cardsStore.current.collectAsStateWithLifecycle()
    // Dernière surface affichée : conservée pendant l'animation de fermeture.
    var lastCards by remember { mutableStateOf<DisplayedSurface?>(null) }
    SideEffect { cards?.let { lastCards = it } }

    HomeHubTheme(settings = settings) {
        CompositionLocalProvider(LocalArtworkLoader provides artworkLoader) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HubColors.Background),
            ) {
                AnimatedBackground(style = settings.wallpaper, modifier = Modifier.fillMaxSize())

                // Retour : écran précédent, ou rien sur l'accueil (un launcher ne se ferme pas).
                BackHandler(enabled = true) { navigator.back() }

                // Retour sur des cartes : elles se ferment (prioritaire sur la navigation).
                BackHandler(enabled = cards != null) { cardsStore.dismiss(null, DismissReason.USER) }

                // Les cartes remplacent l'écran courant, qui reprend son focus à leur fermeture.
                AnimatedContent(
                    targetState = cards != null,
                    transitionSpec = { (fadeIn(tween(320)) + scaleIn(initialScale = 0.97f)) togetherWith fadeOut(tween(200)) },
                    modifier = Modifier.fillMaxSize(),
                    label = "cards",
                ) { showCards ->
                    val displayed = cards ?: lastCards
                    if (showCards && displayed != null) {
                        CardsOverlay(
                            displayed = displayed,
                            onAction = cardsStore::activate,
                            onTimeout = { cardsStore.dismiss(displayed.surface.id, DismissReason.TIMEOUT) },
                        )
                    } else {
                        Screens(navigator, homeViewModel, settingsViewModel, settings)
                    }
                }

                // Confirmation des commandes reçues de la télécommande mobile.
                RemoteFeedbackBanner(events = remoteFeedback)
            }
        }
    }
}

/** Écrans du launcher, avec transitions. */
@Composable
private fun Screens(
    navigator: Navigator,
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    settings: LauncherSettings,
) {
    AnimatedContent(
        targetState = navigator.current,
        transitionSpec = { screenTransition() },
        modifier = Modifier.fillMaxSize(),
        label = "screens",
    ) { destination ->
        when (destination) {
            Destination.Home -> HomeScreen(
                viewModel = homeViewModel,
                userName = settings.userName,
                onOpenAllApps = { navigator.navigate(Destination.AllApps) },
                onOpenSettings = { navigator.navigate(Destination.Settings) },
            )
            Destination.AllApps -> AllAppsScreen(viewModel = homeViewModel)
            Destination.Settings -> SettingsScreen(
                viewModel = settingsViewModel,
                settings = settings,
            )
        }
    }
}
