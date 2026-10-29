package com.homehub.remote.data

import com.homehub.protocol.AuthParams
import com.homehub.protocol.BoxInfo
import com.homehub.protocol.ClientInfo
import com.homehub.protocol.ErrorCodes
import com.homehub.protocol.Events
import com.homehub.protocol.HelloParams
import com.homehub.protocol.HelloResult
import com.homehub.protocol.JsonRpc
import com.homehub.protocol.Method
import com.homehub.protocol.Methods
import com.homehub.protocol.PairParams
import com.homehub.protocol.Protocol
import com.homehub.protocol.RpcException
import com.homehub.protocol.RpcMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Client de l'API locale v2 : JSON-RPC 2.0 sur WebSocket (docs/api/local-api.md).
 *
 * Machine à états :
 * ```
 * Idle → Connecting → (NeedsPairing ⇄) Connected
 *                ↘ Failed            ↘ coupure → Connecting(reconnecting) → …
 * ```
 * - [call] envoie une requête typée et attend sa réponse (ou son erreur).
 * - [notify] envoie une notification sans réponse (retours visuels).
 * - Après une coupure, la connexion est retentée automatiquement avec un
 *   délai croissant ; l'interface reste sur la télécommande pendant ce temps.
 */
class RemoteClient(
    private val store: PhoneStore,
    private val deviceName: String,
    private val appVersion: String,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(Protocol.KEEP_ALIVE_SECONDS.toLong(), TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionJob: Job? = null

    @Volatile
    private var socket: WebSocket? = null
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonElement>>()
    private val nextId = AtomicLong(1)

    private val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = stateFlow.asStateFlow()

    private val eventFlow = MutableSharedFlow<RpcMessage.Call>(extraBufferCapacity = 32)

    /** Événements de la TV autres que l'état de session (`card.action`, `cards.dismissed`…). */
    val events: SharedFlow<RpcMessage.Call> = eventFlow.asSharedFlow()

    /** Se connecte à une box (et abandonne la connexion précédente). */
    fun connect(box: BoxAddress) {
        sessionJob?.cancel()
        sessionJob = scope.launch { runWithRetries(box) }
    }

    /** Ferme la connexion ; l'état revient à [ConnectionState.Idle]. */
    fun disconnect() {
        sessionJob?.cancel()
        sessionJob = null
        socket?.close(NORMAL_CLOSURE, null)
        socket = null
        stateFlow.value = ConnectionState.Idle
    }

    /**
     * Appel typé d'une méthode de la TV.
     * @throws RpcException erreur renvoyée par la TV, TV non connectée ([NOT_CONNECTED]) ou sans réponse ([TIMEOUT]).
     */
    suspend fun <P, R> call(method: Method<P, R>, params: P, timeoutMs: Long = CALL_TIMEOUT_MS): R {
        val webSocket = socket ?: throw RpcException(NOT_CONNECTED, "TV non connectée")
        val id = nextId.getAndIncrement()
        val response = CompletableDeferred<JsonElement>()
        pending[id] = response
        try {
            if (!webSocket.send(JsonRpc.request(id, method, params))) {
                throw RpcException(NOT_CONNECTED, "TV non connectée")
            }
            val result = withTimeoutOrNull(timeoutMs) { response.await() }
                ?: throw RpcException(TIMEOUT, "La TV ne répond pas")
            return JsonRpc.decodeResult(method.result, result)
        } finally {
            pending.remove(id)
        }
    }

    /** Notification sans réponse ; `false` si la TV n'est pas connectée. */
    fun <P, R> notify(method: Method<P, R>, params: P): Boolean =
        socket?.send(JsonRpc.notification(method, params)) ?: false

    /** Soumet le code d'appairage affiché sur la TV. */
    fun submitPin(pin: String) {
        val pairing = stateFlow.value as? ConnectionState.NeedsPairing ?: return
        stateFlow.value = pairing.copy(submitting = true, error = null)
        scope.launch {
            try {
                val token = call(Methods.SessionPair, PairParams(pin)).token
                store.saveToken(pairing.info.id, token)
                val auth = call(Methods.SessionAuth, AuthParams(token))
                store.saveLastBox(pairing.box)
                stateFlow.value = ConnectionState.Connected(pairing.box, pairing.info, auth.fullControl, capabilities)
            } catch (error: RpcException) {
                stateFlow.update { current ->
                    if (current is ConnectionState.NeedsPairing) {
                        current.copy(
                            submitting = false,
                            error = error.message,
                            pairingOpen = error.code != ErrorCodes.PAIRING_CLOSED,
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }

    @Volatile
    private var capabilities: Set<String> = emptySet()

    private suspend fun runWithRetries(box: BoxAddress) {
        var attempt = 0
        var wasConnected = false
        while (scope.isActive) {
            stateFlow.value = ConnectionState.Connecting(box, reconnecting = wasConnected)
            val outcome = try {
                runSession(box) {
                    wasConnected = true
                    attempt = 0
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: RpcException) {
                if (error.code == ErrorCodes.INCOMPATIBLE_PROTOCOL) SessionEnd.Fatal(error.message) else SessionEnd.Lost(error.message)
            } catch (error: Exception) {
                SessionEnd.Lost(error.message)
            }
            // Session abandonnée volontairement (autre box, arrière-plan) : aucun état à publier.
            currentCoroutineContext().ensureActive()
            when (outcome) {
                is SessionEnd.Fatal -> {
                    stateFlow.value = ConnectionState.Failed(box, outcome.message)
                    return
                }
                is SessionEnd.Lost -> {
                    attempt++
                    if (!wasConnected && attempt >= MAX_INITIAL_ATTEMPTS) {
                        stateFlow.value = ConnectionState.Failed(
                            box,
                            "Impossible de joindre « ${box.name} ». Vérifiez que la TV est allumée, " +
                                "que HomeHub y est à jour (0.3 ou plus) et que le téléphone est sur le même Wi-Fi.",
                        )
                        return
                    }
                    delay(backoff(attempt))
                }
            }
        }
    }

    /** Une session complète : ouverture, authentification, puis attente de la coupure. */
    private suspend fun runSession(box: BoxAddress, onAuthenticated: () -> Unit): SessionEnd = coroutineScope {
        val opened = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<SessionEnd>()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onIncoming(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(NORMAL_CLOSURE, null)
                closed.complete(SessionEnd.Lost(reason))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.complete(SessionEnd.Lost(reason))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                opened.completeExceptionally(t)
                closed.complete(SessionEnd.Lost(t.message))
            }
        }
        val webSocket = http.newWebSocket(Request.Builder().url(box.url).build(), listener)
        var pairingWatcher: Job? = null
        try {
            withTimeout(OPEN_TIMEOUT_MS) { opened.await() }
            socket = webSocket

            val hello = call(Methods.SessionHello, HelloParams(client = ClientInfo(store.deviceId(), deviceName, appVersion)))
            capabilities = hello.capabilities.toSet()
            val currentBox = box.copy(boxId = hello.box.id, name = hello.box.name)

            val token = store.token(hello.box.id)
            val authenticated = token != null && authenticate(currentBox, hello.box, token)
            if (authenticated) {
                onAuthenticated()
            } else {
                stateFlow.value = ConnectionState.NeedsPairing(currentBox, hello.box, hello.pairingOpen)
                // Tant que l'écran d'appairage est fermé sur la TV, on surveille son ouverture.
                pairingWatcher = launch { watchPairing(onAuthenticated) }
            }
            closed.await()
        } finally {
            pairingWatcher?.cancel()
            if (socket === webSocket) socket = null
            webSocket.cancel()
            pending.values.forEach { it.completeExceptionally(RpcException(NOT_CONNECTED, "Connexion perdue")) }
            pending.clear()
        }
    }

    /** `true` si le jeton est accepté ; un jeton révoqué est oublié. */
    private suspend fun authenticate(box: BoxAddress, info: BoxInfo, token: String): Boolean = try {
        val auth = call(Methods.SessionAuth, AuthParams(token))
        store.saveLastBox(box)
        stateFlow.value = ConnectionState.Connected(box, info, auth.fullControl, capabilities)
        true
    } catch (error: RpcException) {
        if (error.code != ErrorCodes.UNKNOWN_TOKEN) throw error
        store.clearToken(info.id)
        false
    }

    private suspend fun watchPairing(onAuthenticated: () -> Unit) {
        while (currentCoroutineContext().isActive) {
            when (val current = stateFlow.value) {
                is ConnectionState.Connected -> {
                    onAuthenticated()
                    return
                }
                is ConnectionState.NeedsPairing -> if (!current.pairingOpen) {
                    delay(PAIRING_POLL_MS)
                    val hello: HelloResult = runCatching {
                        call(Methods.SessionHello, HelloParams(client = ClientInfo(store.deviceId(), deviceName, appVersion)))
                    }.getOrNull() ?: continue
                    stateFlow.update { state ->
                        if (state is ConnectionState.NeedsPairing) state.copy(pairingOpen = hello.pairingOpen) else state
                    }
                } else {
                    delay(PAIRING_POLL_MS)
                }
                else -> return
            }
        }
    }

    /** Trame reçue : réponse à un appel, ou événement de la TV. */
    private fun onIncoming(text: String) {
        val message = runCatching { JsonRpc.parse(text) }.getOrNull() ?: return
        when (message) {
            is RpcMessage.Result -> pending[message.id.asLong() ?: return]?.complete(message.result)
            is RpcMessage.Error -> message.id?.asLong()?.let { pending[it]?.completeExceptionally(message.error) }
            is RpcMessage.Call -> if (message.method != Events.Status.name) {
                eventFlow.tryEmit(message)
            } else {
                val status = runCatching { JsonRpc.decodeParams(Events.Status.params, message.params) }.getOrNull() ?: return
                stateFlow.update { current ->
                    when (current) {
                        is ConnectionState.Connected -> current.copy(fullControl = status.fullControl)
                        is ConnectionState.NeedsPairing -> current.copy(pairingOpen = status.pairingOpen)
                        else -> current
                    }
                }
            }
        }
    }

    private fun JsonElement.asLong(): Long? = (this as? JsonPrimitive)?.longOrNull

    private fun backoff(attempt: Int): Long =
        (RETRY_BASE_MS * (1L shl (attempt - 1).coerceIn(0, 4))).coerceAtMost(8_000L)

    private sealed interface SessionEnd {
        data class Lost(val reason: String?) : SessionEnd
        data class Fatal(val message: String) : SessionEnd
    }

    companion object {
        /** Erreurs locales du client (hors codes de l'API). */
        const val NOT_CONNECTED = -1
        const val TIMEOUT = -2

        private const val NORMAL_CLOSURE = 1000
        private const val CONNECT_TIMEOUT_MS = 4_000L
        private const val OPEN_TIMEOUT_MS = 5_000L
        private const val CALL_TIMEOUT_MS = 6_000L
        private const val MAX_INITIAL_ATTEMPTS = 3
        private const val RETRY_BASE_MS = 600L
        private const val PAIRING_POLL_MS = 2_500L
    }
}
