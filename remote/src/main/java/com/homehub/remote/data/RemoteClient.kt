package com.homehub.remote.data

import com.homehub.protocol.ClientMessage
import com.homehub.protocol.ErrorCode
import com.homehub.protocol.MessageCodec
import com.homehub.protocol.Protocol
import com.homehub.protocol.ServerMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Connexion à une box HomeHub (TCP, une ligne JSON par message).
 *
 * Machine à états :
 * ```
 * Idle → Connecting → (NeedsPairing ⇄) Connected
 *                ↘ Failed            ↘ coupure → Connecting(reconnecting) → …
 * ```
 * Après une coupure, la connexion est retentée automatiquement avec un délai
 * croissant ; l'interface reste sur la télécommande pendant ce temps.
 */
class RemoteClient(
    private val store: PhoneStore,
    private val deviceName: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionJob: Job? = null
    private var outgoing: Channel<String>? = null

    private val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = stateFlow.asStateFlow()

    private val incoming = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)

    /** Messages applicatifs reçus : résultats, applications, icônes. */
    val messages: SharedFlow<ServerMessage> = incoming.asSharedFlow()

    /** Se connecte à une box (et abandonne la connexion précédente). */
    fun connect(box: BoxAddress) {
        sessionJob?.cancel()
        sessionJob = scope.launch { runWithRetries(box) }
    }

    /** Ferme la connexion ; l'état revient à [ConnectionState.Idle]. */
    fun disconnect() {
        sessionJob?.cancel()
        sessionJob = null
        outgoing = null
        stateFlow.value = ConnectionState.Idle
    }

    /** Envoie une commande ; ignorée si la session n'est pas prête. */
    fun send(message: ClientMessage): Boolean {
        val channel = outgoing ?: return false
        val allowed = when (message) {
            is ClientMessage.Pair, is ClientMessage.Auth, ClientMessage.Ping -> true
            else -> stateFlow.value is ConnectionState.Connected
        }
        return allowed && channel.trySend(MessageCodec.encode(message)).isSuccess
    }

    /** Soumet le code d'appairage affiché sur la TV. */
    fun submitPin(pin: String) {
        stateFlow.update { if (it is ConnectionState.NeedsPairing) it.copy(submitting = true, error = null) else it }
        send(ClientMessage.Pair(pin))
    }

    private suspend fun runWithRetries(box: BoxAddress) {
        var attempt = 0
        var wasConnected = false
        while (scope.isActive) {
            stateFlow.value = ConnectionState.Connecting(box, reconnecting = wasConnected)
            val outcome = try {
                runSession(box) { wasConnected = true; attempt = 0 }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: IOException) {
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
                            "Impossible de joindre « ${box.name} ». Vérifiez que la TV est allumée " +
                                "et que le téléphone est sur le même Wi-Fi.",
                        )
                        return
                    }
                    delay(backoff(attempt))
                }
            }
        }
    }

    /** Une session complète : connexion, authentification, lecture jusqu'à la coupure. */
    private suspend fun runSession(box: BoxAddress, onAuthenticated: () -> Unit): SessionEnd = coroutineScope {
        val socket = Socket()
        try {
            runInterruptible { socket.connect(InetSocketAddress(box.host, box.port), CONNECT_TIMEOUT_MS) }
            socket.tcpNoDelay = true
            socket.soTimeout = READ_TIMEOUT_MS

            // Les lectures bloquantes d'un socket ignorent l'interruption : à l'annulation,
            // on ferme le socket, ce qui débloque immédiatement la lecture en cours.
            val closer = launch {
                try {
                    awaitCancellation()
                } finally {
                    runCatching { socket.close() }
                }
            }

            val channel = Channel<String>(Channel.UNLIMITED)
            outgoing = channel
            val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
            val writerJob = launch {
                for (line in channel) {
                    runInterruptible {
                        writer.write(line)
                        writer.write("\n")
                        writer.flush()
                    }
                }
            }
            val pingJob = launch {
                while (isActive) {
                    delay(Protocol.KEEP_ALIVE_MS)
                    channel.trySend(MessageCodec.encode(ClientMessage.Ping))
                }
            }

            channel.trySend(MessageCodec.encode(ClientMessage.Hello(store.deviceId(), deviceName)))

            var currentBox = box
            var info: ServerMessage.Info? = null
            var pairingPoll: Job? = null
            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)

            try {
                while (isActive) {
                    val line = runInterruptible { reader.readLine() } ?: return@coroutineScope SessionEnd.Lost(null)
                    val message = MessageCodec.decodeServer(line) ?: continue
                    when (message) {
                        is ServerMessage.Info -> {
                            info = message
                            currentBox = box.copy(boxId = message.boxId, name = message.boxName)
                            val token = store.token(message.boxId)
                            // Sans jeton, une authentification vide déclenche la réponse « appairage requis ».
                            channel.trySend(MessageCodec.encode(ClientMessage.Auth(token ?: NO_TOKEN)))
                        }

                        is ServerMessage.Welcome -> {
                            pairingPoll?.cancel()
                            val boxInfo = info ?: continue
                            store.saveLastBox(currentBox)
                            onAuthenticated()
                            stateFlow.value = ConnectionState.Connected(currentBox, boxInfo, message.fullControl)
                        }

                        is ServerMessage.PairingRequired -> {
                            val boxInfo = info ?: continue
                            store.clearToken(boxInfo.boxId)
                            stateFlow.value = ConnectionState.NeedsPairing(currentBox, boxInfo, message.pairingOpen)
                            // Tant que l'écran d'appairage est fermé sur la TV, on surveille son ouverture.
                            pairingPoll?.cancel()
                            if (!message.pairingOpen) {
                                pairingPoll = launch {
                                    delay(PAIRING_POLL_MS)
                                    channel.trySend(MessageCodec.encode(ClientMessage.Auth(NO_TOKEN)))
                                }
                            }
                        }

                        is ServerMessage.Paired -> {
                            info?.let { store.saveToken(it.boxId, message.token) }
                        }

                        is ServerMessage.Status -> stateFlow.update { current ->
                            if (current is ConnectionState.Connected) current.copy(fullControl = message.fullControl) else current
                        }

                        is ServerMessage.Error -> when (message.code) {
                            ErrorCode.WRONG_PIN, ErrorCode.PAIRING_CLOSED, ErrorCode.TOO_MANY_ATTEMPTS ->
                                stateFlow.update { current ->
                                    if (current is ConnectionState.NeedsPairing) {
                                        current.copy(
                                            error = message.message,
                                            submitting = false,
                                            pairingOpen = message.code != ErrorCode.PAIRING_CLOSED,
                                        )
                                    } else {
                                        current
                                    }
                                }
                            ErrorCode.INCOMPATIBLE_VERSION -> return@coroutineScope SessionEnd.Fatal(message.message)
                            else -> incoming.tryEmit(message)
                        }

                        ServerMessage.Pong -> Unit
                        else -> incoming.tryEmit(message)
                    }
                }
                SessionEnd.Lost(null)
            } finally {
                pairingPoll?.cancel()
                closer.cancel()
                pingJob.cancel()
                writerJob.cancel()
                channel.close()
                if (outgoing === channel) outgoing = null
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun backoff(attempt: Int): Long = (RETRY_BASE_MS * (1L shl (attempt - 1).coerceIn(0, 4))).coerceAtMost(8_000L)

    private sealed interface SessionEnd {
        data class Lost(val reason: String?) : SessionEnd
        data class Fatal(val message: String) : SessionEnd
    }

    private companion object {
        const val NO_TOKEN = "-"
        const val CONNECT_TIMEOUT_MS = 4_000
        const val READ_TIMEOUT_MS = 40_000
        const val MAX_INITIAL_ATTEMPTS = 3
        const val RETRY_BASE_MS = 600L
        const val PAIRING_POLL_MS = 2_500L
    }
}
