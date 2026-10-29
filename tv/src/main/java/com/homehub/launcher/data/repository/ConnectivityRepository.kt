package com.homehub.launcher.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import com.homehub.launcher.data.models.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * État réseau observé via le callback système du réseau par défaut.
 *
 * Aucune interrogation périodique : le système prévient HomeHub à chaque
 * changement, et le callback est désenregistré 5 s après que l'accueil
 * cesse d'être affiché.
 */
class ConnectivityRepository(
    context: Context,
    scope: CoroutineScope,
) {
    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(WifiManager::class.java)

    val status: StateFlow<ConnectionStatus> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            trySend(ConnectionStatus.Offline)
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.toStatus())
            }

            override fun onLost(network: Network) {
                trySend(ConnectionStatus.Offline)
            }
        }
        trySend(currentStatus())
        val registered = runCatching { manager.registerDefaultNetworkCallback(callback) }.isSuccess
        awaitClose {
            if (registered) runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), currentStatus())

    /** Lecture ponctuelle de l'état courant. */
    private fun currentStatus(): ConnectionStatus {
        val manager = connectivityManager ?: return ConnectionStatus.Offline
        val capabilities = runCatching {
            manager.getNetworkCapabilities(manager.activeNetwork)
        }.getOrNull() ?: return ConnectionStatus.Offline
        return capabilities.toStatus()
    }

    private fun NetworkCapabilities.toStatus(): ConnectionStatus = when {
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionStatus.Ethernet
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionStatus.Wifi(wifiLevel(this))
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> ConnectionStatus.Ethernet
        else -> ConnectionStatus.Offline
    }

    /** Convertit le RSSI en niveau 0..3. */
    @Suppress("DEPRECATION")
    private fun wifiLevel(capabilities: NetworkCapabilities): Int {
        val fromCapabilities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities.signalStrength.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }
        } else {
            null
        }
        val rssi = fromCapabilities
            ?: runCatching { wifiManager?.connectionInfo?.rssi }.getOrNull()
            ?: return ConnectionStatus.Wifi.MAX_LEVEL
        return WifiManager.calculateSignalLevel(rssi, ConnectionStatus.Wifi.MAX_LEVEL + 1)
            .coerceIn(0, ConnectionStatus.Wifi.MAX_LEVEL)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
