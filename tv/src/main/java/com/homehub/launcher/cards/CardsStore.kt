package com.homehub.launcher.cards

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.homehub.protocol.Card
import com.homehub.protocol.CardActionEvent
import com.homehub.protocol.CardKind
import com.homehub.protocol.CardPatch
import com.homehub.protocol.CardSurface
import com.homehub.protocol.CardsDismissedEvent
import com.homehub.protocol.DismissReason
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Surface de cartes affichée sur la TV, avec ses images déjà décodées.
 * @property revision incrémentée à chaque mise à jour (relance le délai d'affichage).
 */
@Immutable
data class DisplayedSurface(
    val surface: CardSurface,
    val images: Map<String, ImageBitmap>,
    val revision: Long,
)

/** Événement à transmettre au téléphone. */
sealed interface CardEvent {
    data class Action(val event: CardActionEvent) : CardEvent
    data class Dismissed(val event: CardsDismissedEvent) : CardEvent
}

/**
 * État des cartes de la TV : une seule surface visible à la fois.
 *
 * Écrit par le CardHandler (commandes du téléphone), lu par l'interface,
 * et source des événements `card.action` / `cards.dismissed` renvoyés au
 * téléphone. Toutes les méthodes s'exécutent sur le thread principal.
 */
class CardsStore {

    private val currentState = MutableStateFlow<DisplayedSurface?>(null)
    val current: StateFlow<DisplayedSurface?> = currentState.asStateFlow()

    private val eventFlow = MutableSharedFlow<CardEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<CardEvent> = eventFlow.asSharedFlow()

    private var revision = 0L

    fun show(surface: CardSurface, images: Map<String, ImageBitmap>) {
        currentState.value?.let { previous ->
            if (previous.surface.id != surface.id) emitDismissed(previous.surface.id, DismissReason.REPLACED)
        }
        currentState.value = DisplayedSurface(surface, images, ++revision)
    }

    /** Applique une modification ; `false` si la surface n'est plus affichée. */
    fun update(surfaceId: String, patch: CardPatch, images: Map<String, ImageBitmap>): Boolean {
        val displayed = currentState.value?.takeIf { it.surface.id == surfaceId } ?: return false
        currentState.value = DisplayedSurface(
            surface = displayed.surface.apply(patch),
            images = displayed.images + images,
            revision = ++revision,
        )
        return true
    }

    /** Ferme la surface (toutes si [surfaceId] est nul). */
    fun dismiss(surfaceId: String?, reason: DismissReason): Boolean {
        val displayed = currentState.value ?: return false
        if (surfaceId != null && displayed.surface.id != surfaceId) return false
        currentState.value = null
        if (reason != DismissReason.REMOTE) emitDismissed(displayed.surface.id, reason)
        return true
    }

    /** L'utilisateur active une action de carte sur la TV : le téléphone décide de la suite. */
    fun activate(cardId: String, actionId: String) {
        val surfaceId = currentState.value?.surface?.id ?: return
        eventFlow.tryEmit(CardEvent.Action(CardActionEvent(surfaceId, cardId, actionId)))
    }

    private fun emitDismissed(surfaceId: String, reason: DismissReason) {
        eventFlow.tryEmit(CardEvent.Dismissed(CardsDismissedEvent(surfaceId, reason)))
    }

    private fun CardSurface.apply(patch: CardPatch): CardSurface {
        val replacements = patch.replaceCards.associateBy { it.id }
        var cards = cards.map { replacements[it.id] ?: it } + patch.appendCards
        patch.bodyAppend?.let { text ->
            val index = cards.indexOfFirst { it.kind == CardKind.ANSWER }
            if (index >= 0) {
                cards = cards.toMutableList().also { list ->
                    val card: Card = list[index]
                    list[index] = card.copy(body = card.body.orEmpty() + text)
                }
            }
        }
        return copy(
            state = patch.state ?: state,
            title = patch.title ?: title,
            subtitle = patch.subtitle ?: subtitle,
            cards = cards,
        )
    }
}
