package com.homehub.launcher.remote

import com.homehub.protocol.ErrorCodes
import com.homehub.protocol.JsonRpc
import com.homehub.protocol.Protocol
import com.homehub.protocol.RpcException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.java_websocket.WebSocket
import org.java_websocket.framing.CloseFrame
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

/**
 * Serveur WebSocket de l'API locale v2 (docs/adr/0003).
 *
 * - Un seul chemin accepté : [Protocol.PATH].
 * - Chaque connexion a sa file de messages traitée **dans l'ordre** : deux
 *   appuis rapides sur le pavé ne peuvent pas s'inverser.
 * - Les pings WebSocket détectent les téléphones disparus (Wi-Fi coupé).
 */
class RpcServer(
    port: Int,
    private val router: RpcRouter,
) : WebSocketServer(InetSocketAddress(port)) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = CompletableDeferred<Boolean>()
    private val connectedState = MutableStateFlow(0)

    /** Nombre de téléphones authentifiés. */
    val connectedPhones: StateFlow<Int> = connectedState.asStateFlow()

    init {
        isReuseAddr = true
        isTcpNoDelay = true
        connectionLostTimeout = CONNECTION_LOST_TIMEOUT_SECONDS
    }

    /** Démarre l'écoute ; `false` si le port est indisponible. */
    suspend fun startAndAwait(): Boolean {
        start()
        return withTimeoutOrNull(START_TIMEOUT_MS) { started.await() } ?: false
    }

    /** Arrête le serveur et ferme les connexions. */
    fun shutdown() {
        runCatching { stop(STOP_TIMEOUT_MS) }
        scope.cancel()
        connectedState.value = 0
    }

    /** Envoie une trame (événement) à tous les téléphones authentifiés. */
    fun sendToPaired(text: String) {
        connections.forEach { connection ->
            val state = connection.getAttachment<Connection>() ?: return@forEach
            if (state.session.authenticated && connection.isOpen) runCatching { connection.send(text) }
        }
    }

    override fun onStart() {
        started.complete(true)
    }

    override fun onOpen(connection: WebSocket, handshake: ClientHandshake) {
        val path = handshake.resourceDescriptor?.substringBefore('?')
        if (path != Protocol.PATH) {
            connection.close(CloseFrame.POLICY_VALIDATION, "Chemin inconnu : utilisez ${Protocol.PATH}")
            return
        }
        val state = Connection(RpcSession(), Channel(INBOX_CAPACITY))
        connection.setAttachment(state)
        scope.launch {
            for (text in state.inbox) {
                val wasAuthenticated = state.session.authenticated
                val reply = router.handle(state.session, text)
                if (reply != null && connection.isOpen) runCatching { connection.send(reply) }
                if (wasAuthenticated != state.session.authenticated) refreshCount()
            }
        }
    }

    override fun onMessage(connection: WebSocket, message: String) {
        val state = connection.getAttachment<Connection>() ?: return
        if (state.inbox.trySend(message).isFailure && connection.isOpen) {
            runCatching {
                connection.send(JsonRpc.error(null, RpcException(ErrorCodes.RATE_LIMITED, "File pleine, ralentissez")))
            }
        }
    }

    override fun onClose(connection: WebSocket, code: Int, reason: String?, remote: Boolean) {
        connection.getAttachment<Connection>()?.inbox?.close()
        refreshCount()
    }

    override fun onError(connection: WebSocket?, error: Exception) {
        // Sans connexion associée, l'erreur concerne le serveur lui-même (port occupé…).
        if (connection == null) started.complete(false)
    }

    private fun refreshCount() {
        connectedState.value = connections.count { connection ->
            connection.isOpen && connection.getAttachment<Connection>()?.session?.authenticated == true
        }
    }

    private class Connection(
        val session: RpcSession,
        val inbox: Channel<String>,
    )

    private companion object {
        const val INBOX_CAPACITY = 64
        const val CONNECTION_LOST_TIMEOUT_SECONDS = 30
        const val START_TIMEOUT_MS = 5_000L
        const val STOP_TIMEOUT_MS = 1_000
    }
}
