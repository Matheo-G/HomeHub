package com.homehub.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Modèle de cartes (docs/adr/0008, docs/api/local-api.md §4.3).
 *
 * Le téléphone construit les cartes, la TV les rend. Une carte est une
 * donnée : aucun code, aucun HTML. Un `kind` inconnu d'une TV plus ancienne
 * est rendu par la carte générique.
 */

@Serializable
enum class CardKind {
    @SerialName("answer") ANSWER,
    @SerialName("web") WEB,
    @SerialName("video") VIDEO,
    @SerialName("music") MUSIC,
    @SerialName("app") APP,
    @SerialName("person") PERSON,
    @SerialName("weather") WEATHER,
    @SerialName("widget") WIDGET,
    @SerialName("notification") NOTIFICATION,

    /** Valeur de repli pour un type ajouté par une version plus récente. */
    @SerialName("generic") GENERIC,
}

@Serializable
enum class ImageRatio {
    @SerialName("16:9") LANDSCAPE,
    @SerialName("2:3") POSTER,
    @SerialName("1:1") SQUARE,
}

/**
 * Image d'une carte. La TV n'accède jamais à Internet (docs/adr/0002) :
 * - [data] : image JPEG/PNG encodée en Base64, envoyée par le téléphone (≤ 60 Ko) ;
 * - [app] : utiliser l'icône ou la bannière d'une application installée sur la TV.
 */
@Serializable
data class CardImage(
    val data: String? = null,
    val app: String? = null,
    val ratio: ImageRatio = ImageRatio.POSTER,
)

/**
 * Action proposée sur une carte. Son activation sur la TV est renvoyée au
 * téléphone (événement `card.action`), qui décide de la suite.
 */
@Serializable
data class CardAction(
    val id: String,
    val label: String,
)

@Serializable
data class Card(
    val id: String,
    val kind: CardKind = CardKind.GENERIC,
    val title: String,
    val subtitle: String? = null,
    val body: String? = null,
    val image: CardImage? = null,
    val badges: List<String> = emptyList(),
    val actions: List<CardAction> = emptyList(),
    /** Rangée d'affichage sur la TV (« Films et séries », « Chercher dans… »). */
    val section: String? = null,
)

@Serializable
enum class SurfaceLayout {
    @SerialName("answer") ANSWER,
    @SerialName("results") RESULTS,
    @SerialName("focus") FOCUS,
    @SerialName("loading") LOADING,
}

@Serializable
enum class SurfaceState {
    @SerialName("loading") LOADING,
    @SerialName("thinking") THINKING,
    @SerialName("streaming") STREAMING,
    @SerialName("done") DONE,
    @SerialName("error") ERROR,
}

/** Ensemble de cartes affiché sur la TV (panneau de réponse, grille de résultats…). */
@Serializable
data class CardSurface(
    val id: String,
    val layout: SurfaceLayout = SurfaceLayout.RESULTS,
    val title: String,
    val subtitle: String? = null,
    val state: SurfaceState = SurfaceState.DONE,
    val cards: List<Card> = emptyList(),
    val ttlMs: Long? = null,
)

/** Modification partielle d'une surface (`cards.update`). */
@Serializable
data class CardPatch(
    val state: SurfaceState? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val appendCards: List<Card> = emptyList(),
    /** Cartes remplacées par identifiant (position conservée). */
    val replaceCards: List<Card> = emptyList(),
    /** Texte ajouté au corps de la première carte `answer` (réponse en flux). */
    val bodyAppend: String? = null,
)

@Serializable
data class CardsShowParams(val surface: CardSurface)

@Serializable
data class CardsShowResult(val shown: Boolean = true)

@Serializable
data class CardsUpdateParams(val surfaceId: String, val patch: CardPatch)

@Serializable
data class CardsUpdateResult(val updated: Boolean = true)

@Serializable
data class CardsDismissParams(val surfaceId: String)

@Serializable
data class CardsDismissResult(val dismissed: Boolean = true)

/** Événement : l'utilisateur a activé une action de carte sur la TV. */
@Serializable
data class CardActionEvent(
    val surfaceId: String,
    val cardId: String,
    val actionId: String,
)

@Serializable
enum class DismissReason {
    @SerialName("user") USER,
    @SerialName("timeout") TIMEOUT,
    @SerialName("replaced") REPLACED,
    @SerialName("remote") REMOTE,
}

/** Événement : une surface a disparu de la TV. */
@Serializable
data class CardsDismissedEvent(
    val surfaceId: String,
    val reason: DismissReason,
)
