package com.homehub.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.homehub.launcher.data.models.Artwork
import com.homehub.launcher.data.repository.AppsRepository
import com.homehub.launcher.di.AppContainer
import com.homehub.launcher.ui.HomeHubApp
import com.homehub.launcher.ui.components.ArtworkLoader
import com.homehub.launcher.ui.navigation.Navigator
import com.homehub.launcher.viewmodel.HomeViewModel
import com.homehub.launcher.viewmodel.SettingsViewModel

/**
 * Activité unique de HomeHub, déclarée comme écran d'accueil (catégorie HOME).
 *
 * En `singleTask`, un appui sur Home alors que HomeHub est déjà affiché
 * arrive dans [onNewIntent] : on revient alors simplement à l'accueil.
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as HomeHubApplication).container

    private val homeViewModel: HomeViewModel by viewModels { HomeViewModel.factory(container) }
    private val settingsViewModel: SettingsViewModel by viewModels { SettingsViewModel.factory(container) }

    private val navigator = Navigator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()

        val artworkLoader = container.appsRepository.asArtworkLoader()
        setContent {
            HomeHubApp(
                navigator = navigator,
                homeViewModel = homeViewModel,
                settingsViewModel = settingsViewModel,
                artworkLoader = artworkLoader,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        homeViewModel.onLauncherResumed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            navigator.resetToHome()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    /** Plein écran : aucune barre système, même sur une box qui en afficherait. */
    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

/** Adapte le repository à l'interface attendue par l'UI. */
private fun AppsRepository.asArtworkLoader(): ArtworkLoader = object : ArtworkLoader {
    override fun peek(packageName: String): Artwork? = peekArtwork(packageName)
    override suspend fun load(packageName: String): Artwork? = loadArtwork(packageName)
}
