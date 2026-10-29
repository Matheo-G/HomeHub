package com.homehub.protocol

/*
 * Messages du protocole. Chaque message est encodé en JSON par [MessageCodec]
 * avec un champ "t" (type) court, pour des trames compactes.
 */

/** Messages envoyés par le téléphone à la TV. */
sealed interface ClientMessage {

    /** Ouverture de session : identifie le téléphone. */
    data class Hello(val deviceId: String, val deviceName: String, val version: Int = Protocol.VERSION) : ClientMessage

    /** Authentification avec le jeton reçu lors d'un appairage précédent. */
    data class Auth(val token: String) : ClientMessage

    /** Appairage avec le code affiché sur la TV. */
    data class Pair(val pin: String) : ClientMessage

    /** Appui sur une touche. */
    data class Key(val key: RemoteKey) : ClientMessage

    /** Phrase reconnue par le micro du téléphone, interprétée par la TV. */
    data class Voice(val query: String) : ClientMessage

    /** Texte à placer dans le champ de saisie actif de la TV. */
    data class Text(val text: String) : ClientMessage

    /** Ouverture d'une application. */
    data class Launch(val packageName: String) : ClientMessage

    /** Demande de la liste des applications. */
    data object Apps : ClientMessage

    /** Demande de l'icône d'une application. */
    data class Icon(val packageName: String) : ClientMessage

    /** Maintien de connexion. */
    data object Ping : ClientMessage
}

/** Messages envoyés par la TV au téléphone. */
sealed interface ServerMessage {

    /** Identité et capacités de la box, envoyées en réponse à [ClientMessage.Hello]. */
    data class Info(
        val boxId: String,
        val boxName: String,
        val sdk: Int,
        val version: Int = Protocol.VERSION,
    ) : ServerMessage

    /** Session authentifiée : les commandes sont acceptées. */
    data class Welcome(val fullControl: Boolean) : ServerMessage

    /**
     * Le téléphone doit être appairé.
     * @property pairingOpen vrai si l'écran d'appairage est ouvert sur la TV.
     */
    data class PairingRequired(val pairingOpen: Boolean) : ServerMessage

    /** Appairage réussi : jeton à conserver pour les prochaines connexions. */
    data class Paired(val token: String) : ServerMessage

    /** Résultat d'une commande vocale ou textuelle, à afficher au téléphone. */
    data class Result(val ok: Boolean, val message: String) : ServerMessage

    /** Liste des applications lançables. */
    data class Apps(val apps: List<RemoteApp>) : ServerMessage

    /** Icône PNG d'une application (`null` si indisponible). */
    data class Icon(val packageName: String, val png: ByteArray?) : ServerMessage {
        override fun equals(other: Any?): Boolean =
            other is Icon && other.packageName == packageName && other.png.contentEquals(png)

        override fun hashCode(): Int = 31 * packageName.hashCode() + (png?.contentHashCode() ?: 0)
    }

    /** L'état du contrôle complet (service d'accessibilité) a changé. */
    data class Status(val fullControl: Boolean) : ServerMessage

    /** Erreur de protocole ou d'authentification. */
    data class Error(val code: ErrorCode, val message: String) : ServerMessage

    /** Réponse à [ClientMessage.Ping]. */
    data object Pong : ServerMessage
}

/** Application exposée à la télécommande. */
data class RemoteApp(
    val packageName: String,
    val label: String,
    val favorite: Boolean,
)

/** Codes d'erreur du protocole. */
enum class ErrorCode {
    /** Code d'appairage incorrect. */
    WRONG_PIN,

    /** L'écran d'appairage n'est pas ouvert sur la TV. */
    PAIRING_CLOSED,

    /** Trop d'essais : un nouveau code a été généré. */
    TOO_MANY_ATTEMPTS,

    /** Commande envoyée avant authentification. */
    UNAUTHORIZED,

    /** Versions de protocole incompatibles. */
    INCOMPATIBLE_VERSION,

    /** Message illisible. */
    BAD_REQUEST,
    ;

    companion object {
        fun fromWire(value: String?): ErrorCode = entries.firstOrNull { it.name == value } ?: BAD_REQUEST
    }
}
