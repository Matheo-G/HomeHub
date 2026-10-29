package com.homehub.launcher.remote

import com.homehub.launcher.data.models.AppInfo
import com.homehub.protocol.RemoteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Grammaire vocale française : phrases typiques dictées au téléphone. */
class VoiceCommandInterpreterTest {

    private val interpreter = VoiceCommandInterpreter()

    private val netflix = AppInfo("com.netflix.ninja", "Netflix", isTvApp = true)
    private val youtube = AppInfo("com.google.android.youtube.tv", "YouTube", isTvApp = true)
    private val deezer = AppInfo("deezer.android.tv", "Deezer", isTvApp = true)
    private val prime = AppInfo("com.amazon.amazonvideo.livingroom", "Prime Video", isTvApp = true)
    private val plex = AppInfo("com.plexapp.android", "Plex", isTvApp = true)
    private val apps = listOf(netflix, youtube, deezer, prime, plex)

    private fun interpret(query: String) = interpreter.interpret(query, apps)

    @Test
    fun opensAppsWithVariousVerbs() {
        assertEquals(VoiceAction.OpenApp(netflix), interpret("Ouvre Netflix"))
        assertEquals(VoiceAction.OpenApp(youtube), interpret("lance YouTube s'il te plaît"))
        assertEquals(VoiceAction.OpenApp(prime), interpret("va sur Prime Video"))
        assertEquals(VoiceAction.OpenApp(plex), interpret("Plex"))
        assertEquals(VoiceAction.OpenApp(netflix), interpret("ouvre l'application Netflix"))
    }

    @Test
    fun resolvesAliases() {
        assertEquals(VoiceAction.OpenApp(prime), interpret("ouvre Amazon"))
        assertEquals(VoiceAction.OpenApp(prime), interpret("lance prime"))
    }

    @Test
    fun searchesInsideAnApp() {
        assertEquals(VoiceAction.Search("Stranger Things", netflix), interpret("Cherche Stranger Things sur Netflix"))
        assertEquals(VoiceAction.Search("recettes de crêpes", youtube), interpret("recherche des recettes de crêpes sur YouTube"))
    }

    @Test
    fun searchesGloballyWithoutApp() {
        assertEquals(VoiceAction.Search("Inception", null), interpret("cherche Inception"))
    }

    @Test
    fun playsMediaInAnApp() {
        assertEquals(VoiceAction.PlayMedia("Daft Punk", deezer), interpret("Joue Daft Punk sur Deezer"))
        assertEquals(VoiceAction.PlayMedia("jazz", youtube), interpret("mets du jazz sur YouTube"))
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
        assertEquals(VoiceAction.GoHome, interpret("accueil"))
        assertEquals(VoiceAction.OpenHomeHubSettings, interpret("ouvre les paramètres"))
        assertEquals(VoiceAction.OpenSystemSettings, interpret("paramètres Android"))
        assertEquals(VoiceAction.OpenAllApps, interpret("toutes les applications"))
    }

    @Test
    fun keepsUnknownPhrasesAsFreeText() {
        val action = interpret("le seigneur des anneaux")
        assertTrue(action is VoiceAction.FreeText)
        assertEquals("le seigneur des anneaux", (action as VoiceAction.FreeText).text)
    }

    private fun assertKey(expected: RemoteKey, action: VoiceAction) {
        assertTrue("Attendu une touche, obtenu $action", action is VoiceAction.Press)
        assertEquals(expected, (action as VoiceAction.Press).key)
    }
}
