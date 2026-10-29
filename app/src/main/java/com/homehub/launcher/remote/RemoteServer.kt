package com.homehub.launcher.remote

import com.homehub.protocol.ErrorCode
import com.homehub.protocol.MessageCodec
import com.homehub.protocol.Protocol
import com.homehub.protocol.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Serveur TCP de télécommande : un message JSON par ligne.
 *
 * Chaque téléphone obtient sa propre coroutine de lecture ; les réponses
 * passent par un verrou par connexion pour ne jamais entrelacer deux trames.
 * Une connexion silencieuse plus de [READ_TIMEOUT_MS] est fermée (le
 * téléphone envoie un ping toutes les 15 s).
 */
class RemoteServer(
    private val handler: RemoteCommandHandler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val connections = CopyOnWriteArraySet<Connection>()

    private val connectedState = MutableStateFlow(0)

    /** Nombre de téléphones authentifiés. */
    val connectedPhones: StateFlow<Int> = connectedState.asStateFlow()

    /** Démarre l'écoute et renvoie le port réellement utilisé. */
    fun start(): Int {
        serverSocket?.let { return it.localPort }
        val socket = openSocket()
        serverSocket = socket
        acceptJob = scope.launch { acceptLoop(socket) }
        return socket.localPort
    }

    /** Ferme le serveur et toutes les connexions. */
    fun stop() {
        acceptJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
        connections.forEach { it.close() }
        connections.clear()
        connectedState.value = 0
        scope.cancel()
    }

    /** Envoie un message à tous les téléphones authentifiés. */
    fun broadcast(message: ServerMessage) {
        connections.filter { it.session.authenticated }.forEach { connection ->
            scope.launch { connection.send(message) }
        }
    }

    /** Port préféré, ou port libre attribué par le système s'il est pris. */
    private fun openSocket(): ServerSocket = try {
        ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(Protocol.DEFAULT_PORT))
        }
    } catch (_: IOException) {
        ServerSocket(0)
    }

    private suspend fun acceptLoop(server: ServerSocket) {
        while (scope.isActive && !server.isClosed) {
            val client = try {
                server.accept()
            } catch (_: IOException) {
                break
            }
            scope.launch { serve(client) }
        }
    }

    private suspend fun serve(socket: Socket) {
        val connection = try {
            socket.tcpNoDelay = true
            socket.soTimeout = READ_TIMEOUT_MS
            Connection(socket)
        } catch (_: IOException) {
            runCatching { socket.close() }
            return
        }
        connections.add(connection)
        try {
            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
            while (scope.isActive) {
                val line = try {
                    reader.readLine()
                } catch (_: SocketTimeoutException) {
                    null
                } ?: break
                if (line.isBlank()) continue
                val message = MessageCodec.decodeClient(line)
                if (message == null) {
                    connection.send(ServerMessage.Error(ErrorCode.BAD_REQUEST, "Message illisible"))
                    continue
                }
                val wasAuthenticated = connection.session.authenticated
                val replies = runCatching { handler.handle(connection.session, message) }
                    .getOrElse { listOf(ServerMessage.Result(false, "Erreur sur la TV : ${it.message}")) }
                replies.forEach { connection.send(it) }
                if (!wasAuthenticated && connection.session.authenticated) refreshCount()
            }
        } catch (_: IOException) {
            // Connexion perdue : simple fin de session.
        } finally {
            connections.remove(connection)
            connection.close()
            refreshCount()
        }
    }

    private fun refreshCount() {
        connectedState.value = connections.count { it.session.authenticated }
    }

    /** Connexion d'un téléphone : socket, état de session et écriture sérialisée. */
    private class Connection(private val socket: Socket) {
        val session = RemoteSession()
        private val writer: BufferedWriter = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
        private val writeLock = Mutex()

        suspend fun send(message: ServerMessage) {
            val line = MessageCodec.encode(message)
            writeLock.withLock {
                withContext(Dispatchers.IO) {
                    runCatching {
                        writer.write(line)
                        writer.write("\n")
                        writer.flush()
                    }
                }
            }
        }

        fun close() {
            runCatching { socket.close() }
        }
    }

    private companion object {
        const val READ_TIMEOUT_MS = 45_000
    }
}
