package com.homehub.protocol

/**
 * Constantes du protocole de télécommande HomeHub.
 *
 * Transport : TCP sur le réseau local, un message JSON par ligne (UTF-8).
 * Découverte : le launcher publie un service DNS-SD (mDNS) de type
 * [SERVICE_TYPE] ; son identifiant stable est dans l'attribut TXT [TXT_BOX_ID].
 *
 * Séquence de connexion :
 * ```
 * téléphone → Hello(deviceId, nom)
 * TV        → Info(boxId, nom, capacités)
 * téléphone → Auth(jeton)              si un jeton est connu pour ce boxId
 *             sinon : saisie du code affiché sur la TV → Pair(code)
 * TV        → Welcome                  jeton valide
 *             Paired(jeton) + Welcome  appairage réussi
 *             PairingRequired          jeton absent ou révoqué
 * ```
 * Tant que la session n'est pas authentifiée, la TV ignore toute commande.
 */
object Protocol {
    /** Version du protocole : incrémentée à chaque changement incompatible. */
    const val VERSION: Int = 1

    /** Type de service DNS-SD publié par le launcher. */
    const val SERVICE_TYPE: String = "_homehub._tcp"

    /** Port d'écoute préféré (un port libre est choisi s'il est occupé). */
    const val DEFAULT_PORT: Int = 8765

    /** Attribut TXT portant l'identifiant stable de la box. */
    const val TXT_BOX_ID: String = "id"

    /** Longueur du code d'appairage affiché sur la TV. */
    const val PIN_LENGTH: Int = 4

    /** Taille maximale d'un message (protège contre les flux malformés). */
    const val MAX_LINE_LENGTH: Int = 512 * 1024

    /** Intervalle des pings de maintien de connexion côté téléphone. */
    const val KEEP_ALIVE_MS: Long = 15_000L
}
