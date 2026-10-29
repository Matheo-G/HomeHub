package com.homehub.intent

import com.homehub.protocol.Destination
import com.homehub.protocol.RemoteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Étage « règles » du moteur d'intentions : phrases typiques dictées au téléphone. */
class IntentParserTest {

    private val parser = IntentParser()

    private val netflix = AppRef("com.netflix.ninja", "Netflix")
    private val youtube = AppRef("com.google.android.youtube.tv", "YouTube")
    private val deezer = AppRef("deezer.android.tv", "Deezer")
    private val prime = AppRef("com.amazon.amazonvideo.livingroom", "Prime Video")
    private val plex = AppRef("com.plexapp.android", "Plex")
    private val apps = listOf(netflix, youtube, deezer, prime, plex)

    private fun interpret(query: String) = parser.parse(query, apps)

    @Test
    fun opensAppsWithVariousVerbs() {
        assertEquals(UserIntent.LaunchApp(netflix), interpret("Ouvre Netflix"))
        assertEquals(UserIntent.LaunchApp(youtube), interpret("lance YouTube s'il te plaît"))
        assertEquals(UserIntent.LaunchApp(prime), interpret("va sur Prime Video"))
        assertEquals(UserIntent.LaunchApp(plex), interpret("Plex"))
        assertEquals(UserIntent.LaunchApp(netflix), interpret("ouvre l'application Netflix"))
    }

    @Test
    fun resolvesAliases() {
        assertEquals(UserIntent.LaunchApp(prime), interpret("ouvre Amazon"))
        assertEquals(UserIntent.LaunchApp(prime), interpret("lance prime"))
    }

    @Test
    fun searchesInsideAnApp() {
        assertEquals(UserIntent.Search("Stranger Things", netflix), interpret("Cherche Stranger Things sur Netflix"))
        assertEquals(UserIntent.Search("recettes de crêpes", youtube), interpret("recherche des recettes de crêpes sur YouTube"))
    }

    @Test
    fun searchesGloballyWithoutApp() {
        assertEquals(UserIntent.Search("Inception", null), interpret("cherche Inception"))
    }

    @Test
    fun playsMediaInAnApp() {
        assertEquals(UserIntent.PlayMedia("Daft Punk", deezer), interpret("Joue Daft Punk sur Deezer"))
        assertEquals(UserIntent.PlayMedia("jazz", youtube), interpret("mets du jazz sur YouTube"))
    }

    @Test
    fun controlsPlaybackAndVolume() {
        assertKey(RemoteKey.PLAY_PAUSE, interpret("Pause"))
        assertKey(RemoteKey.PLAY_PAUSE, interpret("reprends"))
        assertKey(RemoteKey.NEXT, interpret("épisode suivant"))
        assertKey(RemoteKey.VOLUME_UP, interpret("monte le son"))
        assertKey(RemoteKey.VOLUME_DOWN, interpret("baisse le volume"))
        assertKey(RemoteKey.MUTE, interpret("coupe le son"))
        assertKey(RemoteKey.BACK, interpret("retour"))
        assertKey(RemoteKey.CENTER, interpret("OK"))
    }

    @Test
    fun opensScreens() {
        assertNavigate(Destination.HOME, interpret("accueil"))
        assertNavigate(Destination.SETTINGS, interpret("ouvre les paramètres"))
        assertNavigate(Destination.ANDROID_SETTINGS, interpret("paramètres Android"))
        assertNavigate(Destination.ALL_APPS, interpret("toutes les applications"))
    }

    @Test
    fun keepsUnknownPhrasesAsFreeText() {
        val action = interpret("le seigneur des anneaux")
        assertTrue(action is UserIntent.FreeText)
        assertEquals("le seigneur des anneaux", (action as UserIntent.FreeText).text)
    }

    private fun assertKey(expected: RemoteKey, action: UserIntent) {
        assertTrue("Attendu une touche, obtenu $action", action is UserIntent.Key)
        assertEquals(expected, (action as UserIntent.Key).key)
    }

    private fun assertNavigate(expected: Destination, action: UserIntent) {
        assertTrue("Attendu une navigation, obtenu $action", action is UserIntent.Navigate)
        assertEquals(expected, (action as UserIntent.Navigate).destination)
    }
}
