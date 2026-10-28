package com.homehub.launcher.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Horloge de l'accueil.
 *
 * Le flux ne se réveille qu'au changement de minute, aligné sur la seconde
 * zéro : une seule émission par minute, donc une seule recomposition de la
 * barre d'état — et aucune du reste de l'écran.
 */
object Clock {

    private val locale: Locale = Locale.FRENCH
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)

    /** Émet l'heure courante (ms) immédiatement puis à chaque nouvelle minute. */
    fun minuteTicks(): Flow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(MINUTE_MS - now % MINUTE_MS)
        }
    }

    /** « 21:05 » */
    fun formatTime(epochMillis: Long): String =
        timeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    /** « Mercredi 28 octobre » */
    fun formatDate(epochMillis: Long): String =
        dateFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
            .replaceFirstChar { it.titlecase(locale) }

    /** Salutation adaptée au moment de la journée. */
    fun greeting(epochMillis: Long): String {
        val hour = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).hour
        return when (hour) {
            in 5..17 -> "Bonjour"
            else -> "Bonsoir"
        }
    }

    private const val MINUTE_MS = 60_000L
}
