package com.homehub.launcher.cards

import com.homehub.protocol.Card
import com.homehub.protocol.CardKind
import com.homehub.protocol.CardPatch
import com.homehub.protocol.CardSurface
import com.homehub.protocol.DismissReason
import com.homehub.protocol.SurfaceState
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** État des cartes de la TV : affichage, modifications, fermetures et événements. */
class CardsStoreTest {

    private fun surface(id: String = "s1", vararg cards: Card) =
        CardSurface(id = id, title = "Interstellar", state = SurfaceState.LOADING, cards = cards.toList())

    private val movie = Card(id = "m1", kind = CardKind.VIDEO, title = "Interstellar")
    private val answer = Card(id = "a1", kind = CardKind.ANSWER, title = "Réponse", body = "Christopher")

    @Test
    fun patchReplacesAppendsAndStreamsText() {
        val store = CardsStore()
        store.show(surface("s1", movie, answer), emptyMap())
        val updated = store.update(
            "s1",
            CardPatch(
                state = SurfaceState.DONE,
                replaceCards = listOf(movie.copy(subtitle = "2014")),
                appendCards = listOf(Card(id = "m2", title = "Inception")),
                bodyAppend = " Nolan",
            ),
            emptyMap(),
        )
        assertTrue(updated)
        val shown = store.current.value!!.surface
        assertEquals(SurfaceState.DONE, shown.state)
        assertEquals(listOf("m1", "a1", "m2"), shown.cards.map { it.id })
        assertEquals("2014", shown.cards[0].subtitle)
        assertEquals("Christopher Nolan", shown.cards[1].body)
    }

    @Test
    fun updatesToAnotherSurfaceAreRejected() {
        val store = CardsStore()
        store.show(surface("s1"), emptyMap())
        assertFalse(store.update("autre", CardPatch(title = "x"), emptyMap()))
    }

    @Test
    fun userActionsAndDismissalsAreReportedToThePhone() = runTest(UnconfinedTestDispatcher()) {
        val store = CardsStore()
        val events = mutableListOf<CardEvent>()
        val job = launch { store.events.collect { events += it } }

        store.show(surface("s1", movie), emptyMap())
        store.activate("m1", "watch")
        store.show(surface("s2", movie), emptyMap()) // remplace s1
        store.dismiss(null, DismissReason.USER)

        assertNull(store.current.value)
        assertEquals(
            listOf(
                CardEvent.Action(com.homehub.protocol.CardActionEvent("s1", "m1", "watch")),
                CardEvent.Dismissed(com.homehub.protocol.CardsDismissedEvent("s1", DismissReason.REPLACED)),
                CardEvent.Dismissed(com.homehub.protocol.CardsDismissedEvent("s2", DismissReason.USER)),
            ),
            events,
        )
        job.cancel()
    }

    @Test
    fun remoteDismissalIsNotEchoed() = runTest(UnconfinedTestDispatcher()) {
        val store = CardsStore()
        val events = mutableListOf<CardEvent>()
        val job = launch { store.events.collect { events += it } }
        store.show(surface("s1"), emptyMap())
        store.dismiss("s1", DismissReason.REMOTE)
        assertTrue(events.isEmpty())
        job.cancel()
    }
}
