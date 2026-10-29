package com.homehub.launcher

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.homehub.launcher.data.models.Artwork
import com.homehub.launcher.data.repository.AppsRepository
import com.homehub.launcher.di.AppContainer
import com.homehub.launcher.remote.LauncherForeground
import com.homehub.launcher.remote.RemoteControlService
import com.homehub.launcher.remote.RemoteKeyBus
import com.homehub.launcher.ui.HomeHubApp
import com.homehub.launcher.ui.components.ArtworkLoader
import com.homehub.launcher.ui.navigation.Destination
import com.homehub.launcher.ui.navigation.Navigator
import com.homehub.launcher.viewmodel.HomeViewModel
import com.homehub.launcher.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Activité unique de HomeHub, déclarée comme écran d'accueil (catégorie HOME).
 *
 * En `singleTask`, un appui sur Home alors que HomeHub est déjà affiché
 * arrive dans [onNewIntent] : on revient alors simplement à l'accueil.
 *
 * Elle relaie aussi la télécommande mobile : les touches reçues pendant que
 * HomeHub est au premier plan sont rejouées dans sa fenêtre, exactement comme
 * celles de la télécommande physique.
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
        handleDestination(intent)

        val artworkLoader = container.appsRepository.asArtworkLoader()
        setContent {
            HomeHubApp(
                navigator = navigator,
                homeViewModel = homeViewModel,
                settingsViewModel = settingsViewModel,
                artworkLoader = artworkLoader,
                remoteFeedback = container.remoteHub.feedback,
            )
        }

        // Démarre ou arrête le serveur de télécommande selon l'option.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsViewModel.settings
                    .map { it.remoteEnabled }
                    .distinctUntilChanged()
                    .collect { enabled -> RemoteControlService.sync(this@MainActivity, enabled) }
            }
        }

        // Touches de la télécommande mobile, rejouées dans la fenêtre HomeHub.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                RemoteKeyBus.events.collect(::injectKey)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LauncherForeground.isResumed = true
        homeViewModel.onLauncherResumed()
    }

    override fun onPause() {
        LauncherForeground.isResumed = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            navigator.resetToHome()
        }
        handleDestination(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    /** Écran demandé par une commande vocale (« paramètres », « toutes les applications »). */
    private fun handleDestination(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_DESTINATION)) {
            DESTINATION_SETTINGS -> navigator.openFromHome(Destination.Settings)
            DESTINATION_ALL_APPS -> navigator.openFromHome(Destination.AllApps)
        }
        intent?.removeExtra(EXTRA_DESTINATION)
    }

    /** Rejoue un appui (enfoncé + relâché) comme s'il venait de la télécommande physique. */
    private fun injectKey(keyCode: Int) {
        val time = SystemClock.uptimeMillis()
        dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0))
        dispatchKeyEvent(KeyEvent(time, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0))
    }

    /** Plein écran : aucune barre système, même sur une box qui en afficherait. */
    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        /** Extra d'intention : écran HomeHub à afficher. */
        const val EXTRA_DESTINATION = "com.homehub.launcher.DESTINATION"
        const val DESTINATION_SETTINGS = "settings"
        const val DESTINATION_ALL_APPS = "all_apps"
    }
}

/** Adapte le repository à l'interface attendue par l'UI. */
private fun AppsRepository.asArtworkLoader(): ArtworkLoader = object : ArtworkLoader {
    override fun peek(packageName: String): Artwork? = peekArtwork(packageName)
    override suspend fun load(packageName: String): Artwork? = loadArtwork(packageName)
}
