package com.homehub.remote

import android.app.Application
import android.os.Build
import android.provider.Settings
import com.homehub.remote.data.BoxDiscovery
import com.homehub.remote.data.PhoneStore
import com.homehub.remote.data.RemoteClient

/**
 * Point d'entrée : conteneur des singletons de la télécommande
 * (stockage, découverte, connexion), partagés par l'activité et le ViewModel.
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
    val client = RemoteClient(store, deviceName(application))

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
