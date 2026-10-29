package com.homehub.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Paramètres et résultats des méthodes de l'API locale v2.
 * Chaque classe correspond à un tableau de docs/api/local-api.md.
 * Règle de compatibilité : on ajoute des champs (avec valeur par défaut),
 * on n'en retire et on n'en renomme jamais.
 */

/** Paramètres ou résultat vide : `{}`. */
@Serializable
data object Empty

// --- session.* --------------------------------------------------------------

@Serializable
data class ClientInfo(
    val id: String,
    val name: String,
    val version: String = "",
)

@Serializable
data class HelloParams(
    val protocol: Int = Protocol.VERSION,
    val client: ClientInfo,
)

@Serializable
data class BoxInfo(
    val id: String,
    val name: String,
    val model: String = "",
    val sdk: Int = 0,
    val version: String = "",
)

@Serializable
data class HelloResult(
    val protocol: Int = Protocol.VERSION,
    val authRequired: Boolean = true,
    /** Vrai si l'écran d'appairage est ouvert sur la TV (le téléphone peut proposer la saisie du code). */
    val pairingOpen: Boolean = false,
    val box: BoxInfo,
    val capabilities: List<String> = emptyList(),
)

@Serializable
data class AuthParams(val token: String)

@Serializable
data class AuthResult(val fullControl: Boolean = false)

@Serializable
data class PairParams(val pin: String)

@Serializable
data class PairResult(val token: String)

@Serializable
data class SessionStatus(
    val fullControl: Boolean = false,
    val pairingOpen: Boolean = false,
)

// --- input.* -----------------------------------------------------------------

@Serializable
data class KeyParams(
    val key: RemoteKey,
    val action: KeyAction = KeyAction.PRESS,
)

@Serializable
data class TextParams(
    val text: String,
    val mode: String = "replace",
)

@Serializable
data class Applied(val applied: Boolean = true)

// --- app.* -------------------------------------------------------------------

@Serializable
data class AppEntry(
    @SerialName("package") val packageName: String,
    val label: String,
    val favorite: Boolean = false,
    val tv: Boolean = false,
)

@Serializable
data class AppListResult(val apps: List<AppEntry> = emptyList())

@Serializable
data class IconParams(
    @SerialName("package") val packageName: String,
    val size: Int = 128,
)

/** Icône PNG encodée en Base64 ; absente si l'application n'existe plus. */
@Serializable
data class IconResult(val png: String? = null)

@Serializable
data class LaunchParams(@SerialName("package") val packageName: String)

@Serializable
data class LaunchResult(val launched: Boolean = true)

/** Intention d'ouverture de `app.open`. */
@Serializable
enum class OpenIntent {
    /** Ouvre `uri` (deep link), sinon l'application. */
    @SerialName("view") VIEW,

    /** Recherche `query` dans l'application, ou recherche globale de la TV sans `package`. */
    @SerialName("search") SEARCH,

    /** Lance la lecture de `query` (musique, vidéo). */
    @SerialName("playFromSearch") PLAY_FROM_SEARCH,
}

@Serializable
data class OpenParams(
    @SerialName("package") val packageName: String? = null,
    val intent: OpenIntent = OpenIntent.VIEW,
    val uri: String? = null,
    val query: String? = null,
)

/**
 * Résultat de `app.open`.
 * @property fallback ce qui a réellement été fait si l'intention n'a pas été prise en charge
 *                    (`"launched"` : l'application a seulement été ouverte).
 */
@Serializable
data class OpenResult(
    val opened: Boolean = true,
    val fallback: String? = null,
)

// --- ui.* --------------------------------------------------------------------

@Serializable
enum class Destination {
    @SerialName("home") HOME,
    @SerialName("allApps") ALL_APPS,
    @SerialName("settings") SETTINGS,
    @SerialName("androidSettings") ANDROID_SETTINGS,
}

@Serializable
data class NavigateParams(val destination: Destination)

@Serializable
data class NavigateResult(val navigated: Boolean = true)

@Serializable
enum class ToastKind {
    @SerialName("info") INFO,
    @SerialName("success") SUCCESS,
    @SerialName("error") ERROR,
}

/**
 * Message éphémère sur la TV.
 * @property title phrase prononcée par l'utilisateur, affichée au-dessus du message.
 */
@Serializable
data class ToastParams(
    val message: String,
    val title: String? = null,
    val kind: ToastKind = ToastKind.INFO,
)
