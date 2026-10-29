package com.homehub.launcher.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.homehub.launcher.command.CommandDispatcher
import com.homehub.launcher.command.CommandOrigin
import com.homehub.launcher.command.TvCommand
import com.homehub.launcher.data.models.AppInfo
import com.homehub.launcher.data.models.ConnectionStatus
import com.homehub.launcher.data.models.HomeModule
import com.homehub.launcher.data.models.HomeTile
import com.homehub.launcher.data.repository.AppsRepository
import com.homehub.launcher.data.repository.ConnectivityRepository
import com.homehub.launcher.data.repository.RecentAppsRepository
import com.homehub.launcher.data.source.FavoritesCatalog
import com.homehub.launcher.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * État de l'écran d'accueil et de la grille « Toutes les applications ».
 *
 * Chaque information est un flux indépendant : l'UI n'observe que ce dont
 * chaque composant a besoin, si bien qu'un changement réseau ne recompose
 * que l'icône réseau, et une installation ne recompose que les grilles.
 */
class HomeViewModel(
    appsRepository: AppsRepository,
    private val recentAppsRepository: RecentAppsRepository,
    connectivityRepository: ConnectivityRepository,
    private val commands: CommandDispatcher,
) : ViewModel() {

    /** Ordre des blocs de l'accueil (voir [HomeModule] pour en ajouter). */
    val modules: List<HomeModule> = HomeModule.Default

    /** État réseau pour la barre d'état. */
    val connectionStatus: StateFlow<ConnectionStatus> = connectivityRepository.status

    /** Toutes les applications lançables ; `null` pendant le premier chargement. */
    val allApps: StateFlow<List<AppInfo>?> = appsRepository.installedApps

    /** Tuiles favorites résolues selon les applications installées. */
    val favorites: StateFlow<List<HomeTile>?> = appsRepository.installedApps
        .map { apps ->
            apps?.let { list -> FavoritesCatalog.resolve(list.mapTo(HashSet()) { it.packageName }) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Applications récentes encore installées, de la plus récente à la plus ancienne. */
    val recentApps: StateFlow<List<AppInfo>> = combine(
        appsRepository.installedApps,
        recentAppsRepository.recentPackages,
    ) { apps, recents ->
        if (apps == null) {
            emptyList()
        } else {
            val byPackage = apps.associateBy { it.packageName }
            recents.asSequence()
                .filterNot { it in EXCLUDED_PACKAGES }
                .mapNotNull { byPackage[it] }
                .take(MAX_RECENTS)
                .toList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Dernière carte focalisée, pour rendre le focus au bon endroit en revenant
     * sur l'accueil. Valeur d'interface non observée : pas besoin d'un flux.
     */
    var lastFocusedKey: String? = null

    /** Appelé à chaque retour sur le launcher (bouton Home, sortie d'une application). */
    fun onLauncherResumed() {
        recentAppsRepository.refresh()
    }

    /** Ouvre une application et l'ajoute à l'historique. */
    fun openApp(packageName: String) {
        // Même couche que les commandes du téléphone (le handler mémorise aussi le lancement).
        viewModelScope.launch { commands.dispatch(TvCommand.LaunchApp(packageName), CommandOrigin.LOCAL) }
    }

    /** Ouvre la fiche Play Store d'un favori absent. */
    fun openStore(packageName: String) {
        viewModelScope.launch { commands.dispatch(TvCommand.OpenStore(packageName), CommandOrigin.LOCAL) }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val MAX_RECENTS = 12

        /** Launchers système, qu'il serait absurde de proposer dans les récents. */
        private val EXCLUDED_PACKAGES = setOf(
            "com.google.android.leanbacklauncher",
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
        )

        /** Fabrique utilisée par l'activité (injection manuelle). */
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    appsRepository = container.appsRepository,
                    recentAppsRepository = container.recentAppsRepository,
                    connectivityRepository = container.connectivityRepository,
                    commands = container.commandDispatcher,
                )
            }
        }
    }
}
