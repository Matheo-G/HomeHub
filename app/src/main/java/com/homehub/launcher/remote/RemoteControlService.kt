package com.homehub.launcher.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.homehub.launcher.HomeHubApplication
import com.homehub.launcher.R
import com.homehub.protocol.Protocol
import com.homehub.protocol.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Service de premier plan qui rend la TV joignable par la télécommande :
 *  - démarre le [RemoteServer] ;
 *  - publie la box sur le réseau local (DNS-SD) pour que le téléphone la
 *    trouve sans saisir d'adresse ;
 *  - prévient les téléphones quand le contrôle complet est activé ou coupé.
 *
 * Il tourne tant que l'option « Télécommande mobile » est activée, y compris
 * quand une autre application est au premier plan, et redémarre avec la box.
 * Au repos, il ne consomme rien : un thread bloqué en attente de connexion.
 */
class RemoteControlService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var server: RemoteServer? = null
    private var nsdManager: NsdManager? = null
    private var registration: NsdManager.RegistrationListener? = null

    private val container get() = (application as HomeHubApplication).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        scope.launch { startServer() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        unregisterService()
        server?.stop()
        server = null
        container.remoteHub.updateStatus { it.copy(running = false, connectedPhones = 0) }
        super.onDestroy()
    }

    private suspend fun startServer() {
        // L'option a pu être coupée pendant que la box redémarrait.
        if (!container.settingsRepository.settings.first().remoteEnabled) {
            stopSelf()
            return
        }

        val name = boxName()
        val handler = RemoteCommandHandler(
            context = this,
            pairingManager = container.pairingManager,
            appsRepository = container.appsRepository,
            recentAppsRepository = container.recentAppsRepository,
            appLauncher = container.appLauncher,
            remoteHub = container.remoteHub,
            boxName = { name },
        )
        val remoteServer = RemoteServer(handler)
        val port = runCatching { remoteServer.start() }.getOrElse {
            stopSelf()
            return
        }
        server = remoteServer

        container.remoteHub.updateStatus {
            it.copy(running = true, boxName = name, address = localIpAddress(), port = port)
        }
        registerService(name, port, container.pairingManager.boxId())

        scope.launch {
            remoteServer.connectedPhones.collect { count ->
                container.remoteHub.updateStatus { it.copy(connectedPhones = count, address = localIpAddress()) }
            }
        }
        scope.launch {
            AccessibilityBridge.connected.drop(1).collect { enabled ->
                remoteServer.broadcast(ServerMessage.Status(enabled))
            }
        }
    }

    /** Publication DNS-SD : « HomeHub · Mi Box » de type _homehub._tcp. */
    private fun registerService(name: String, port: Int, boxId: String) {
        val manager = getSystemService(NsdManager::class.java) ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "HomeHub · $name"
            serviceType = Protocol.SERVICE_TYPE
            setPort(port)
            setAttribute(Protocol.TXT_BOX_ID, boxId)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        runCatching {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            nsdManager = manager
            registration = listener
        }
    }

    private fun unregisterService() {
        val listener = registration ?: return
        runCatching { nsdManager?.unregisterService(listener) }
        registration = null
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Télécommande mobile", NotificationManager.IMPORTANCE_MIN)
            channel.setShowBadge(false)
            manager?.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("HomeHub")
            .setContentText("Télécommande mobile prête")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    /** Nom de la box tel que défini dans les paramètres Android (« Mi Box »). */
    private fun boxName(): String =
        runCatching { Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: Build.MODEL

    companion object {
        private const val CHANNEL_ID = "remote"
        private const val NOTIFICATION_ID = 42

        /** Démarre ou arrête le service selon l'option des Paramètres. */
        fun sync(context: Context, enabled: Boolean) {
            val intent = Intent(context, RemoteControlService::class.java)
            if (enabled) {
                // Peut être refusé si l'application est en arrière-plan (Android 12+) : sans gravité,
                // le service sera relancé au prochain affichage de HomeHub.
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else {
                context.stopService(intent)
            }
        }

        /** Première adresse IPv4 locale (affichée pour la connexion manuelle). */
        fun localIpAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
