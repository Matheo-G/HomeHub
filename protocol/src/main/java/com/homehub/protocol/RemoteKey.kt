package com.homehub.protocol

/**
 * Touches virtuelles de la télécommande.
 *
 * Le nom de la constante est transmis tel quel : ne renommer une constante
 * qu'en incrémentant [Protocol.VERSION].
 */
enum class RemoteKey {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    CENTER,
    BACK,
    HOME,
    PLAY_PAUSE,
    NEXT,
    PREVIOUS,
    REWIND,
    FAST_FORWARD,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    ;

    /** Touches de navigation (focus), à répéter tant que le doigt reste appuyé. */
    val isRepeatable: Boolean
        get() = this == UP || this == DOWN || this == LEFT || this == RIGHT ||
            this == VOLUME_UP || this == VOLUME_DOWN

    companion object {
        /** Conversion tolérante depuis le texte reçu ; `null` si inconnu. */
        fun fromWire(value: String?): RemoteKey? = entries.firstOrNull { it.name == value }
    }
}
