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
import com.homehub.intent.AppRef
import com.homehub.protocol.AppEntry
import com.homehub.protocol.Empty
import com.homehub.protocol.ErrorCodes
import com.homehub.protocol.IconParams
import com.homehub.protocol.KeyParams
import com.homehub.protocol.LaunchParams
import com.homehub.protocol.Methods
import com.homehub.protocol.Protocol
import com.homehub.protocol.RemoteKey
import com.homehub.protocol.RpcException
import com.homehub.protocol.TextParams
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Base64

/** Écran à afficher, déduit de l'état de connexion. */
enum class RemoteScreen { Connect, Pair, Remote }

/** Bandeau de retour (résultat d'une commande, commande vocale). */
@Immutable
data class Banner(val id: Long, val message: String, val ok: Boolean, val query: String? = null)

/**
 * État et actions de la télécommande.
 *
 * Le téléphone est le cerveau (docs/adr/0002) : la phrase dictée est
 * interprétée ici par le moteur d'intentions, puis le planificateur envoie
 * à la TV des commandes de l'API locale.
 *
 * Cycle de vie : la connexion est ouverte quand l'application est visible et
 * fermée quand elle passe en arrière-plan ; la reconnexion à la dernière box
 * est instantanée au retour.
 */
class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as RemoteApplication).container
    private val client = container.client
    private val discovery = container.discovery
    private val store = container.store
    private val parser = container.intentParser
    private val executor = container.executor

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

    private val appsState = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = appsState.asStateFlow()

    private val iconsState = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val icons: StateFlow<Map<String, ImageBitmap>> = iconsState.asStateFlow()

    // --- Voix -------------------------------------------------------------

    private val recognizer = VoiceRecognizer(application) { text -> sendVoice(text) }
    val voice: StateFlow<VoiceState> = recognizer.state

    /** Faux : l'interface utilise la boîte de dialogue vocale du système. */
    val inlineVoiceAvailable: Boolean = recognizer.isAvailable

    private var textJob: Job? = null
    private var lastHintAt = 0L

    // --- Sources de recherche -------------------------------------------------

    /** Vrai si une clé TMDB est enregistrée (la clé elle-même ne quitte pas le coffre). */
    val tmdbConfigured: StateFlow<Boolean> = container.secrets.tmdbKey
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, !container.secrets.tmdbKey.value.isNullOrBlank())

    /** Vérifie la clé auprès de TMDB puis l'enregistre chiffrée ; `false` si refusée. */
    suspend fun saveTmdbKey(value: String): Boolean {
        val candidate = value.trim()
        if (candidate.isEmpty() || !container.tmdb.verify(candidate)) return false
        container.secrets.setTmdbKey(candidate)
        return true
    }

    fun clearTmdbKey() = container.secrets.setTmdbKey(null)

    init {
        // Le moteur de recherche connaît les applications de la TV.
        viewModelScope.launch { appsState.collect { container.tvApps = it } }
        // Actions et fermetures de cartes sur la TV : le téléphone décide de la suite.
        viewModelScope.launch { client.events.collect { container.search.onEvent(it) } }
        // À chaque nouvelle session authentifiée : liste des applications de la TV.
        viewModelScope.launch {
            client.state
                .map { it is ConnectionState.Connected }
                .distinctUntilChanged()
                .collect { connected -> if (connected) refreshApps() }
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

    // --- Commandes directes -------------------------------------------------

    /** Touche : envoyée immédiatement, dans l'ordre des appuis. */
    fun press(key: RemoteKey) {
        viewModelScope.launch {
            try {
                client.call(Methods.InputKey, KeyParams(key))
            } catch (error: RpcException) {
                // Rappel du contrôle complet, au plus une fois toutes les 20 s.
                val now = System.currentTimeMillis()
                if (error.code == ErrorCodes.FULL_CONTROL_REQUIRED && now - lastHintAt > HINT_INTERVAL_MS) {
                    lastHintAt = now
                    showBanner(error.message, ok = false)
                }
            }
        }
    }

    fun launch(app: AppEntry) {
        viewModelScope.launch {
            try {
                client.call(Methods.AppLaunch, LaunchParams(app.packageName))
                showBanner("Ouverture de ${app.label}", ok = true)
            } catch (error: RpcException) {
                showBanner(error.message, ok = false)
            }
        }
    }

    /** Clavier : texte complet envoyé après une courte pause de frappe. */
    fun typeText(text: String) {
        textJob?.cancel()
        textJob = viewModelScope.launch {
            delay(TEXT_DEBOUNCE_MS)
            try {
                client.call(Methods.InputText, TextParams(text))
            } catch (error: RpcException) {
                if (bannerState.value?.message != error.message) showBanner(error.message, ok = false)
            }
        }
    }

    /** Liste des applications de la TV, puis leurs icônes (4 en parallèle). */
    fun refreshApps() {
        viewModelScope.launch {
            val apps = runCatching { client.call(Methods.AppList, Empty).apps }.getOrNull() ?: return@launch
            appsState.value = apps
            val missing = apps.filter { it.packageName !in iconsState.value }
            val permits = Semaphore(ICON_PARALLELISM)
            missing.forEach { app ->
                launch { permits.withPermit { loadIcon(app.packageName) } }
            }
        }
    }

    // --- Voix ---------------------------------------------------------------

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

    /**
     * Phrase reconnue (micro intégré ou boîte de dialogue système) :
     * interprétation locale, exécution, retour sur le téléphone et sur la TV.
     */
    fun sendVoice(text: String) {
        val query = text.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            if (client.state.value !is ConnectionState.Connected) {
                showBanner("TV non connectée", ok = false, query = query)
                return@launch
            }
            showBanner("…", ok = true, query = query)
            if (appsState.value.isEmpty()) {
                runCatching { client.call(Methods.AppList, Empty).apps }.getOrNull()?.let { appsState.value = it }
            }
            val known = appsState.value.map { AppRef(it.packageName, it.label) }
            val intent = withContext(Dispatchers.Default) { parser.parse(query, known) }
            val outcome = executor.execute(intent)
            showBanner(outcome.message, outcome.ok, query)
            executor.announce(query, outcome)
        }
    }

    // --- Outils -------------------------------------------------------------

    private suspend fun loadIcon(packageName: String) {
        val png = runCatching { client.call(Methods.AppIcon, IconParams(packageName)).png }.getOrNull() ?: return
        val bitmap = withContext(Dispatchers.Default) {
            runCatching {
                val bytes = Base64.getDecoder().decode(png)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        } ?: return
        iconsState.update { it + (packageName to bitmap) }
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
        private const val HINT_INTERVAL_MS = 20_000L
        private const val ICON_PARALLELISM = 4

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                RemoteViewModel(application)
            }
        }
    }
}
