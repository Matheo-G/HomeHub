package com.homehub.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Associe une clé stable à chaque carte focalisable d'un écran, pour pouvoir
 * redonner le focus à une carte précise (premier affichage, retour d'un autre
 * écran).
 */
@Stable
class FocusRegistry {

    private val requesters = HashMap<String, FocusRequester>()

    /** Requester de la carte [key], créé à la première demande. */
    operator fun get(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /**
     * Tente de donner le focus à la carte [key].
     * Renvoie `false` si la carte n'est pas (ou plus) affichée.
     */
    fun tryFocus(key: String): Boolean {
        val requester = requesters[key] ?: return false
        return runCatching { requester.requestFocus() }.isSuccess
    }
}

@Composable
fun rememberFocusRegistry(): FocusRegistry = remember { FocusRegistry() }

/**
 * Attend que la composition soit mesurée et placée : un `requestFocus()`
 * lancé avant échouerait sur un nœud pas encore attaché.
 */
suspend fun awaitLayout() {
    withFrameNanos { }
    withFrameNanos { }
}
