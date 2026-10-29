package com.homehub.remote.viewmodel

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.homehub.protocol.ClientMessage
import com.homehub.protocol.Protocol
import com.homehub.protocol.RemoteApp
import com.homehub.protocol.RemoteKey
import com.homehub.protocol.ServerMessage
import com.homehub.remote.RemoteApplication
import com.homehub.remote.data.BoxAddress
import com.homehub.remote.data.ConnectionState
import com.homehub.remote.voice.VoiceRecognizer
import com.homehub.remote.voice.VoiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Écran à afficher, déduit de l'état de connexion. */
enum class RemoteScreen { Connect, Pair, Remote }

/** Bandeau de retour (résultat d'une commande, envoi vocal). */
@Immutable
data class Banner(val id: Long, val message: String, val ok: Boolean, val query: String? = null)

/**
 * État et actions de la télécommande.
 *
 * Cycle de vie : la connexion est ouverte quand l'application est visible et
 * fermée quand elle passe en arrière-plan (aucune consommation inutile) ;
 * la reconnexion à la dernière box est instantanée au retour.
 */
class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as RemoteApplication).container
    private val client = container.client
    private val discovery = container.discovery
    private val store = container.store

    val connection: StateFlow<ConnectionState> = client.state
    val discoveredBoxes: StateFlow<List<BoxAddress>> = discovery.boxes
    val searching: StateFlow<Boolean> = discovery.searching

    /** L'utilisateur a demandé à changer de box : on reste sur l'écran de choix. */
    private val choosingBox = MutableStateFlow(false)

    val screen: StateFlow<RemoteScreen> = combine(client.state, choosingBox) { state, choosing ->
        when {
            choosing -> RemoteScreen.Connect
            state is ConnectionState.Connected -> RemoteScreen.Remote
            state is ConnectionState.Connecting && state.reconnecting -> RemoteScreen.Remote
            state is ConnectionState.NeedsPairing -> RemoteScreen.Pair
            else -> RemoteScreen.Connect
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, RemoteScreen.Connect)

    private val bannerState = MutableStateFlow<Banner?>(null)
    val banner: StateFlow<Banner?> = bannerState.asStateFlow()
    private var bannerJob: Job? = null

    private val appsState = MutableStateFlow<List<RemoteApp>>(emptyList())
    val apps: StateFlow<List<RemoteApp>> = appsState.asStateFlow()

    private val iconsState = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val icons: StateFlow<Map<String, ImageBitmap>> = iconsState.asStateFlow()

    // --- Voix -------------------------------------------------------------

    private val recognizer = VoiceRecognizer(application) { text -> sendVoice(text) }
    val voice: StateFlow<VoiceState> = recognizer.state

    /** Faux : l'interface utilise la boîte de dialogue vocale du système. */
    val inlineVoiceAvailable: Boolean = recognizer.isAvailable

    private var textJob: Job? = null

    init {
        viewModelScope.launch {
            client.messages.collect(::onServerMessage)
        }
        // À chaque nouvelle session authentifiée : liste des applications.
        viewModelScope.launch {
            client.state
                .map { it is ConnectionState.Connected }
                .distinctUntilChanged()
                .collect { connected -> if (connected) client.send(ClientMessage.Apps) }
        }
    }

    // --- Cycle de vie -----------------------------------------------------

    fun onForeground() {
        discovery.start()
        if (client.state.value is ConnectionState.Idle && !choosingBox.value) {
            viewModelScope.launch { store.lastBox()?.let(client::connect) }
        }
    }

    fun onBackground() {
        recognizer.cancel()
        discovery.stop()
        client.disconnect()
    }

    // --- Connexion --------------------------------------------------------

    fun connect(box: BoxAddress) {
        choosingBox.value = false
        client.connect(box)
    }

    /** Connexion manuelle : « 192.168.1.20 » ou « 192.168.1.20:8765 ». */
    fun connectManual(input: String): Boolean {
        val trimmed = input.trim()
        val host = trimmed.substringBefore(':').trim()
        val port = trimmed.substringAfter(':', "").toIntOrNull() ?: Protocol.DEFAULT_PORT
        if (host.isEmpty() || port !in 1..65_535) return false
        connect(BoxAddress(host = host, port = port, name = host))
        return true
    }

    fun changeBox() {
        choosingBox.value = true
        client.disconnect()
        discovery.restart()
    }

    fun refreshDiscovery() = discovery.restart()

    fun submitPin(pin: String) = client.submitPin(pin)

    // --- Commandes --------------------------------------------------------

    fun press(key: RemoteKey) {
        client.send(ClientMessage.Key(key))
    }

    fun launch(app: RemoteApp) {
        client.send(ClientMessage.Launch(app.packageName))
    }

    /** Clavier : envoi du texte complet, regroupé pour ne pas saturer le réseau. */
    fun typeText(text: String) {
        textJob?.cancel()
        textJob = viewModelScope.launch {
            delay(TEXT_DEBOUNCE_MS)
            client.send(ClientMessage.Text(text))
        }
    }

    fun refreshApps() {
        client.send(ClientMessage.Apps)
    }

    fun startVoice() {
        if (client.state.value !is ConnectionState.Connected) {
            showBanner("Connectez d'abord votre TV", ok = false)
            return
        }
        recognizer.start()
    }

    fun stopVoice() = recognizer.stop()

    fun cancelVoice() = recognizer.cancel()

    fun clearVoiceError() = recognizer.clearError()

    /** Phrase reconnue (micro intégré ou boîte de dialogue système). */
    fun sendVoice(text: String) {
        val query = text.trim()
        if (query.isEmpty()) return
        if (client.send(ClientMessage.Voice(query))) {
            showBanner("Envoyé à la TV…", ok = true, query = query)
        } else {
            showBanner("TV non connectée", ok = false, query = query)
        }
    }

    // --- Réception --------------------------------------------------------

    private suspend fun onServerMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.Result -> {
                val previous = bannerState.value
                // Le résultat d'une commande vocale garde la phrase prononcée.
                val query = previous?.query?.takeIf { previous.message == "Envoyé à la TV…" }
                showBanner(message.message, message.ok, query)
            }
            is ServerMessage.Apps -> {
                appsState.value = message.apps
                requestMissingIcons(message.apps)
            }
            is ServerMessage.Icon -> {
                val png = message.png ?: return
                val bitmap = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(png, 0, png.size)?.asImageBitmap()
                } ?: return
                iconsState.update { it + (message.packageName to bitmap) }
            }
            is ServerMessage.Error -> showBanner(message.message, ok = false)
            else -> Unit
        }
    }

    private fun requestMissingIcons(apps: List<RemoteApp>) {
        val known = iconsState.value.keys
        apps.asSequence()
            .filter { it.packageName !in known }
            .forEach { client.send(ClientMessage.Icon(it.packageName)) }
    }

    private fun showBanner(message: String, ok: Boolean, query: String? = null) {
        if (message.isBlank()) return
        bannerJob?.cancel()
        bannerState.value = Banner(System.nanoTime(), message, ok, query)
        bannerJob = viewModelScope.launch {
            delay(BANNER_DURATION_MS)
            bannerState.value = null
        }
    }

    override fun onCleared() {
        recognizer.destroy()
        super.onCleared()
    }

    companion object {
        private const val TEXT_DEBOUNCE_MS = 250L
        private const val BANNER_DURATION_MS = 4_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                RemoteViewModel(application)
            }
        }
    }
}
