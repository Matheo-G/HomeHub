package com.homehub.launcher.data.models

import androidx.compose.runtime.Immutable

/** État de la connexion réseau affiché dans la barre d'état. */
@Immutable
sealed interface ConnectionStatus {

    /** Aucun réseau disponible. */
    data object Offline : ConnectionStatus

    /** Connexion filaire. */
    data object Ethernet : ConnectionStatus

    /**
     * Connexion Wi-Fi.
     *
     * @property level qualité du signal de 0 (faible) à 3 (excellent).
     */
    data class Wifi(val level: Int) : ConnectionStatus {
        companion object {
            const val MAX_LEVEL: Int = 3
        }
    }
}
