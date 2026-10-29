package com.homehub.launcher.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.homehub.launcher.data.models.AccentColor
import com.homehub.launcher.data.models.CardSize
import com.homehub.launcher.data.models.LauncherSettings
import com.homehub.launcher.data.models.WallpaperStyle
import com.homehub.launcher.data.repository.RecentAppsRepository
import com.homehub.launcher.data.repository.SettingsRepository
import com.homehub.launcher.di.AppContainer
import com.homehub.launcher.remote.AccessibilityBridge
import com.homehub.launcher.remote.PairedPhone
import com.homehub.launcher.remote.PairingManager
import com.homehub.launcher.remote.RemoteHub
import com.homehub.launcher.remote.RemoteStatus
import com.homehub.launcher.utils.AppLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Réglages du launcher, partagés par le thème global et la page Paramètres,
 * ainsi que l'état de la télécommande mobile.
 *
 * Chaque modification est écrite dans DataStore ; le flux [settings] la
 * renvoie aussitôt et toute l'interface s'adapte en direct, avec transitions.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val recentAppsRepository: RecentAppsRepository,
    private val appLauncher: AppLauncher,
    private val pairingManager: PairingManager,
    remoteHub: RemoteHub,
) : ViewModel() {

    val settings: StateFlow<LauncherSettings> = settingsRepository.settings

    private val usageAccess = MutableStateFlow(recentAppsRepository.hasUsageAccess())

    /** Vrai si l'historique d'utilisation système est accessible. */
    val usageAccessGranted: StateFlow<Boolean> = usageAccess.asStateFlow()

    // --- Télécommande mobile ---------------------------------------------

    /** Serveur, adresse et téléphones connectés. */
    val remoteStatus: StateFlow<RemoteStatus> = remoteHub.status

    /** Vrai si le contrôle complet (service d'accessibilité) est actif. */
    val fullControlEnabled: StateFlow<Boolean> = AccessibilityBridge.connected

    /** Code d'appairage affiché ; `null` quand l'écran d'appairage est fermé. */
    val pairingPin: StateFlow<String?> = pairingManager.pairingPin

    /** Téléphones appairés. */
    val pairedPhones: StateFlow<List<PairedPhone>> = pairingManager.pairedPhones

    /** Téléphone qui vient de s'appairer (pour l'animation de succès). */
    val newlyPaired: SharedFlow<PairedPhone> = pairingManager.newlyPaired

    /** Relit les autorisations système (l'utilisateur revient peut-être des Paramètres Android). */
    fun refreshSystemState() {
        usageAccess.value = recentAppsRepository.hasUsageAccess()
    }

    fun setAccent(accent: AccentColor) {
        viewModelScope.launch { settingsRepository.setAccent(accent) }
    }

    fun setWallpaper(wallpaper: WallpaperStyle) {
        viewModelScope.launch { settingsRepository.setWallpaper(wallpaper) }
    }

    fun setCardSize(cardSize: CardSize) {
        viewModelScope.launch { settingsRepository.setCardSize(cardSize) }
    }

    fun setRemoteEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRemoteEnabled(enabled) }
    }

    fun openPairing() = pairingManager.openPairing()

    fun closePairing() = pairingManager.closePairing()

    fun forgetPhones() {
        viewModelScope.launch { pairingManager.forgetAll() }
    }

    fun openSystemSettings() {
        appLauncher.openSystemSettings()
    }

    fun openUsageAccessSettings() {
        appLauncher.openUsageAccessSettings()
    }

    fun openAccessibilitySettings() {
        appLauncher.openAccessibilitySettings()
    }

    override fun onCleared() {
        pairingManager.closePairing()
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsRepository = container.settingsRepository,
                    recentAppsRepository = container.recentAppsRepository,
                    appLauncher = container.appLauncher,
                    pairingManager = container.pairingManager,
                    remoteHub = container.remoteHub,
                )
            }
        }
    }
}
