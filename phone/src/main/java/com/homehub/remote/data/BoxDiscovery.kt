package com.homehub.remote.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.homehub.protocol.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Découverte automatique des box HomeHub sur le Wi-Fi (DNS-SD / mDNS).
 *
 * Android ne résout qu'un service à la fois : les services trouvés passent
 * par une file traitée séquentiellement. Un verrou multicast est tenu
 * pendant la recherche, certains téléphones filtrant sinon les annonces.
 */
class BoxDiscovery(context: Context) {

    private val appContext = context.applicationContext
    private val nsdManager: NsdManager? = appContext.getSystemService(NsdManager::class.java)
    private val wifiManager: WifiManager? = appContext.getSystemService(WifiManager::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listener: NsdManager.DiscoveryListener? = null
    private var resolveJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    /** Services trouvés, en attente de résolution d'adresse. */
    private var pending = Channel<NsdServiceInfo>(Channel.UNLIMITED)

    private val boxesState = MutableStateFlow<List<BoxAddress>>(emptyList())
    val boxes: StateFlow<List<BoxAddress>> = boxesState.asStateFlow()

    private val searchingState = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = searchingState.asStateFlow()

    /** Nom de service DNS-SD → box résolue (pour gérer les disparitions). */
    private val byServiceName = LinkedHashMap<String, BoxAddress>()

    fun start() {
        val manager = nsdManager ?: return
        if (listener != null) return

        multicastLock = wifiManager?.createMulticastLock("homehub-discovery")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }

        pending = Channel(Channel.UNLIMITED)
        resolveJob = scope.launch {
            for (service in pending) {
                resolve(manager, service)?.let { box ->
                    byServiceName[service.serviceName] = box
                    publish()
                }
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                searchingState.value = true
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                pending.trySend(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                scope.launch {
                    byServiceName.remove(service.serviceName)
                    publish()
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                searchingState.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                searchingState.value = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        listener = discoveryListener
        runCatching {
            manager.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.onFailure {
            listener = null
            searchingState.value = false
        }
    }

    fun stop() {
        listener?.let { runCatching { nsdManager?.stopServiceDiscovery(it) } }
        listener = null
        resolveJob?.cancel()
        pending.close()
        runCatching { multicastLock?.release() }
        multicastLock = null
        searchingState.value = false
    }

    /** Relance une recherche propre (bouton « Actualiser »). */
    fun restart() {
        stop()
        byServiceName.clear()
        publish()
        start()
    }

    private fun publish() {
        boxesState.value = byServiceName.values.distinctBy { it.key }.sortedBy { it.name }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(manager: NsdManager, service: NsdServiceInfo): BoxAddress? =
        withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val resolveListener = object : NsdManager.ResolveListener {
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host?.hostAddress
                        val attributes = info.attributes
                        val protocols = attributes[Protocol.TXT_PROTOCOLS]?.let { String(it) }.orEmpty()
                        // Seules les TV parlant le protocole de cette application sont proposées.
                        val compatible = protocols.split(',').any { it.trim() == Protocol.VERSION.toString() }
                        val box = host?.takeIf { compatible }?.let {
                            BoxAddress(
                                host = it,
                                port = info.port,
                                name = info.serviceName.removePrefix("HomeHub · ").ifBlank { "HomeHub" },
                                boxId = attributes[Protocol.TXT_BOX_ID]?.let { bytes -> String(bytes) },
                                path = attributes[Protocol.TXT_PATH]?.let { bytes -> String(bytes) } ?: Protocol.PATH,
                            )
                        }
                        if (continuation.isActive) continuation.resume(box)
                    }

                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                runCatching { manager.resolveService(service, resolveListener) }
                    .onFailure { if (continuation.isActive) continuation.resume(null) }
            }
        }

    private companion object {
        const val RESOLVE_TIMEOUT_MS = 6_000L
    }
}
