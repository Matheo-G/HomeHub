package com.homehub.launcher.remote

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * Ponts entre la télécommande et les composants dont le système contrôle le
 * cycle de vie (activité, service d'accessibilité). Ces objets ne peuvent pas
 * être injectés : ils sont donc exposés comme singletons de processus, avec
 * une surface volontairement minimale.
 */

/** Indique si l'écran HomeHub est actuellement au premier plan. */
object LauncherForeground {
    @Volatile
    var isResumed: Boolean = false
}

/**
 * Touches à rejouer dans l'activité HomeHub quand elle est au premier plan.
 * Le tampon absorbe les appuis rapides (répétition du pavé directionnel).
 */
object RemoteKeyBus {
    private val keys = MutableSharedFlow<Int>(extraBufferCapacity = 32)

    /** Codes `KeyEvent.KEYCODE_*` à injecter dans la fenêtre HomeHub. */
    val events: SharedFlow<Int> = keys.asSharedFlow()

    fun send(keyCode: Int): Boolean = keys.tryEmit(keyCode)
}

/**
 * Accès au service d'accessibilité, instancié par le système quand
 * l'utilisateur l'active (Paramètres Android → Accessibilité → HomeHub).
 */
object AccessibilityBridge {
    private val connectedState = MutableStateFlow(false)

    /** Vrai tant que le « contrôle complet » est actif. */
    val connected: StateFlow<Boolean> = connectedState.asStateFlow()

    @Volatile
    var service: HomeHubAccessibilityService? = null
        private set

    internal fun attach(instance: HomeHubAccessibilityService) {
        service = instance
        connectedState.value = true
    }

    internal fun detach(instance: HomeHubAccessibilityService) {
        if (service === instance) {
            service = null
            connectedState.value = false
        }
    }
}

/**
 * Retour visuel d'une commande reçue du téléphone.
 *
 * @property query phrase prononcée, si la commande était vocale.
 * @property message action effectuée (ou raison de l'échec).
 */
@Immutable
data class RemoteFeedback(
    val query: String?,
    val message: String,
    val ok: Boolean,
)

/** État du serveur de télécommande, affiché dans les Paramètres. */
@Immutable
data class RemoteStatus(
    val running: Boolean = false,
    val boxName: String = "",
    val address: String? = null,
    val port: Int = 0,
    val connectedPhones: Int = 0,
)

/**
 * Point central de la télécommande côté TV : état du serveur et retours
 * visuels. Créé une fois par [com.homehub.launcher.di.AppContainer].
 */
class RemoteHub {
    private val statusState = MutableStateFlow(RemoteStatus())
    val status: StateFlow<RemoteStatus> = statusState.asStateFlow()

    private val feedbackEvents = MutableSharedFlow<RemoteFeedback>(extraBufferCapacity = 4)
    val feedback: SharedFlow<RemoteFeedback> = feedbackEvents.asSharedFlow()

    fun updateStatus(transform: (RemoteStatus) -> RemoteStatus) {
        statusState.value = transform(statusState.value)
    }

    fun showFeedback(feedback: RemoteFeedback) {
        feedbackEvents.tryEmit(feedback)
    }
}
