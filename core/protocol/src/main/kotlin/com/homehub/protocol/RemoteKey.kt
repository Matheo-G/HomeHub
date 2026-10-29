package com.homehub.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Touches de la télécommande (`input.key`). */
@Serializable
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

    /** Touches à répéter tant que le doigt reste appuyé. */
    val isRepeatable: Boolean
        get() = this == UP || this == DOWN || this == LEFT || this == RIGHT ||
            this == VOLUME_UP || this == VOLUME_DOWN
}

/** Phase d'un appui. */
@Serializable
enum class KeyAction {
    @SerialName("press") PRESS,
    @SerialName("down") DOWN,
    @SerialName("up") UP,
}
