package com.homehub.intent

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mesure le taux de compréhension du moteur de règles sur le corpus de
 * phrases (corpus.tsv). Objectif V3 (docs/roadmap.md) : > 85 % sans IA ;
 * le seuil du test est volontairement plus haut pour ce corpus de base.
 */
class CorpusTest {

    private val apps = listOf(
        AppRef("com.netflix.ninja", "Netflix"),
        AppRef("com.google.android.youtube.tv", "YouTube"),
        AppRef("deezer.android.tv", "Deezer"),
        AppRef("com.amazon.amazonvideo.livingroom", "Prime Video"),
        AppRef("com.plexapp.android", "Plex"),
    )
    private val parser = IntentParser()

    @Test
    fun corpusIsUnderstood() {
        val lines = javaClass.classLoader!!.getResource("corpus.tsv")!!.readText()
            .lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        val failures = lines.mapNotNull { line ->
            val (phrase, expected) = line.split('\t').let { it[0] to it[1] }
            val actual = describe(parser.parse(phrase, apps))
            if (actual == expected) null else "« $phrase » → $actual (attendu $expected)"
        }
        val rate = 1.0 - failures.size.toDouble() / lines.size
        println("Corpus : ${lines.size} phrases, ${"%.1f".format(rate * 100)} % comprises")
        failures.forEach(::println)
        assertTrue("Taux de compréhension insuffisant (${"%.1f".format(rate * 100)} %) :\n${failures.joinToString("\n")}", rate >= 0.95)
    }

    private fun describe(intent: UserIntent): String = when (intent) {
        is UserIntent.LaunchApp -> "launch:${intent.app.label}"
        is UserIntent.Search -> "search:${intent.query}@${intent.app?.label ?: "*"}"
        is UserIntent.PlayMedia -> "play:${intent.query}@${intent.app?.label ?: "*"}"
        is UserIntent.Key -> "key:${intent.key.name}"
        is UserIntent.Navigate -> "nav:${intent.destination.name}"
        is UserIntent.FreeText -> "free"
    }
}
