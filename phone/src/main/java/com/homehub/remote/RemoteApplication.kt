package com.homehub.remote

import android.app.Application
import android.os.Build
import android.provider.Settings
import com.homehub.remote.data.BoxDiscovery
import com.homehub.remote.data.PhoneStore
import com.homehub.intent.IntentParser
import com.homehub.remote.data.RemoteClient
import com.homehub.protocol.AppEntry
import com.homehub.remote.planner.IntentExecutor
import com.homehub.remote.planner.SearchCoordinator
import com.homehub.remote.secrets.SecretStore
import com.homehub.sources.InstalledApp
import com.homehub.sources.InstalledAppsSource
import com.homehub.sources.SearchAggregator
import com.homehub.sources.TmdbSource
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Point d'entrée : conteneur des singletons de la télécommande
 * (stockage, découverte, connexion, moteur d'intentions), partagés par
 * l'activité et le ViewModel.
 */
class RemoteApplication : Application() {

    lateinit var container: RemoteContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = RemoteContainer(this)
    }
}

class RemoteContainer(application: Application) {
    val store = PhoneStore(application)
    val discovery = BoxDiscovery(application)
    val client = RemoteClient(store, deviceName(application), BuildConfig.VERSION_NAME)

    /** Secrets chiffrés (clés des sources, puis des fournisseurs d'IA). */
    val secrets = SecretStore(application)

    /** Client HTTP partagé des sources Internet (le téléphone seul accède à Internet). */
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    /** Applications de la TV connues du téléphone (alimentées par `app.list`). */
    @Volatile
    var tvApps: List<AppEntry> = emptyList()

    val tmdb = TmdbSource(http, credential = { secrets.tmdbKey.value })

    private val aggregator = SearchAggregator(
        listOf(
            tmdb,
            InstalledAppsSource { tvApps.map { InstalledApp(it.packageName, it.label) } },
        ),
    )

    /** Moteur d'intentions (règles, docs/adr/0006), recherche universelle et planificateur. */
    val intentParser = IntentParser()
    val search = SearchCoordinator(client, aggregator, http) { tvApps }
    val executor = IntentExecutor(client, search)

    /** Nom affiché sur la TV lors de l'appairage (« Pixel 8 »). */
    private fun deviceName(application: Application): String {
        val userName = runCatching {
            Settings.Global.getString(application.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()
        if (!userName.isNullOrBlank()) return userName
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        return if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "$manufacturer ${Build.MODEL}"
    }
}
