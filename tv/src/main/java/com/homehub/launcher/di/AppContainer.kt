package com.homehub.launcher.di

import android.content.Context
import com.homehub.launcher.cards.CardsStore
import com.homehub.launcher.command.AppHandler
import com.homehub.launcher.command.CardHandler
import com.homehub.launcher.command.CommandDispatcher
import com.homehub.launcher.command.FeedbackHandler
import com.homehub.launcher.command.InputHandler
import com.homehub.launcher.command.NavigationHandler
import com.homehub.launcher.data.repository.AppsRepository
import com.homehub.launcher.data.repository.ConnectivityRepository
import com.homehub.launcher.data.repository.RecentAppsRepository
import com.homehub.launcher.data.repository.SettingsRepository
import com.homehub.launcher.data.source.homeHubDataStore
import com.homehub.launcher.remote.InputController
import com.homehub.launcher.remote.PairingManager
import com.homehub.launcher.remote.RemoteHub
import com.homehub.launcher.utils.AppLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Conteneur de dépendances de l'application (injection manuelle).
 *
 * Pas de framework d'injection pour une V1 : ce conteneur, créé une seule fois
 * par [com.homehub.launcher.HomeHubApplication], instancie paresseusement les
 * singletons. Une nouvelle intégration (météo, agenda, NAS…) y ajoute son
 * repository ; migrer vers Hilt plus tard ne changera que ce fichier.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Portée de vie de l'application, pour les flux partagés entre écrans. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val appsRepository: AppsRepository by lazy {
        AppsRepository(appContext, applicationScope)
    }

    val recentAppsRepository: RecentAppsRepository by lazy {
        RecentAppsRepository(appContext, appContext.homeHubDataStore)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext.homeHubDataStore, applicationScope)
    }

    val connectivityRepository: ConnectivityRepository by lazy {
        ConnectivityRepository(appContext, applicationScope)
    }

    val appLauncher: AppLauncher by lazy {
        AppLauncher(appContext)
    }

    /** Télécommande mobile : appairage des téléphones. */
    val pairingManager: PairingManager by lazy {
        PairingManager(appContext.homeHubDataStore, applicationScope)
    }

    /** Télécommande mobile : état du serveur et retours visuels. */
    val remoteHub: RemoteHub by lazy { RemoteHub() }

    /**
     * Couche de commandes unique (docs/architecture.md §5.2) : utilisée par le
     * serveur réseau comme par l'interface locale.
     */
    val commandDispatcher: CommandDispatcher by lazy {
        CommandDispatcher(
            input = InputHandler(InputController(appContext, appLauncher)),
            apps = AppHandler(appLauncher, recentAppsRepository),
            navigation = NavigationHandler(appLauncher),
            feedback = FeedbackHandler(appContext, remoteHub),
            cards = CardHandler(cardsStore, appLauncher),
        )
    }

    /** Cartes affichées (résultats de recherche, réponses) et événements associés. */
    val cardsStore: CardsStore by lazy { CardsStore() }
}
