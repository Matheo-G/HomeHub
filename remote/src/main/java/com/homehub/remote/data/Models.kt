package com.homehub.remote.data

import androidx.compose.runtime.Immutable
import com.homehub.protocol.ServerMessage

/**
 * Box HomeHub joignable sur le réseau local.
 *
 * @property boxId identifiant stable annoncé par la box (absent pour une
 *                 adresse saisie à la main, connu après la première connexion).
 */
@Immutable
data class BoxAddress(
    val host: String,
    val port: Int,
    val name: String,
    val boxId: String? = null,
) {
    /** Deux annonces de la même box (Wi-Fi + Ethernet…) ont le même identifiant. */
    val key: String get() = boxId ?: "$host:$port"
}

/** État de la connexion à la box. */
sealed interface ConnectionState {

    /** Aucune box choisie. */
    data object Idle : ConnectionState

    /**
     * Connexion en cours.
     * @property reconnecting vrai après une coupure (l'interface reste sur la télécommande).
     */
    data class Connecting(val box: BoxAddress, val reconnecting: Boolean) : ConnectionState

    /**
     * La box attend un code d'appairage.
     * @property pairingOpen vrai si l'écran d'appairage est affiché sur la TV.
     * @property error dernier refus (code faux, trop d'essais…).
     */
    data class NeedsPairing(
        val box: BoxAddress,
        val info: ServerMessage.Info,
        val pairingOpen: Boolean,
        val error: String? = null,
        val submitting: Boolean = false,
    ) : ConnectionState

    /** Session authentifiée : la télécommande est utilisable. */
    data class Connected(
        val box: BoxAddress,
        val info: ServerMessage.Info,
        val fullControl: Boolean,
    ) : ConnectionState

    /** Échec de connexion (box éteinte, mauvais réseau…). */
    data class Failed(val box: BoxAddress, val message: String) : ConnectionState
}
