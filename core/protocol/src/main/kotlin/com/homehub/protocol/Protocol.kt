package com.homehub.protocol

/**
 * Constantes du protocole local HomeHub v2.
 *
 * Spécification : docs/api/local-api.md. Décision : docs/adr/0003.
 */
object Protocol {
    /** Version majeure du protocole (rupture de compatibilité uniquement). */
    const val VERSION: Int = 2

    /** Type de service DNS-SD publié par la TV. */
    const val SERVICE_TYPE: String = "_homehub._tcp"

    /** Port d'écoute préféré. */
    const val DEFAULT_PORT: Int = 8765

    /** Chemin du point d'accès WebSocket. */
    const val PATH: String = "/homehub"

    /** Attributs TXT de l'annonce DNS-SD. */
    const val TXT_BOX_ID: String = "id"
    const val TXT_PROTOCOLS: String = "proto"
    const val TXT_PATH: String = "path"

    /** Longueur du code d'appairage. */
    const val PIN_LENGTH: Int = 4

    /** Taille maximale d'une trame (caractères). */
    const val MAX_MESSAGE_LENGTH: Int = 256 * 1024

    /** Débit maximal par session (messages par seconde). */
    const val MAX_MESSAGES_PER_SECOND: Int = 50

    /** Intervalle de maintien de connexion. */
    const val KEEP_ALIVE_SECONDS: Int = 15
}

/** Capacités annoncées par la TV dans `session.hello`. */
object Capabilities {
    const val INPUT_FULL_CONTROL: String = "input.fullControl"
    const val APP_OPEN: String = "app.open"
    const val UI_TOAST: String = "ui.toast"
    const val CARDS: String = "cards"
    const val WIDGETS: String = "widgets"
    const val NOTIFICATIONS: String = "notifications"
}
