package com.homehub.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

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

                // Confirmation des commandes reçues de la télécommande mobile.
                RemoteFeedbackBanner(events = remoteFeedback)
            }
        }
    }
}
