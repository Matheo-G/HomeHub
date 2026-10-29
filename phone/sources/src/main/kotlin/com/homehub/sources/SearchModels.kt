package com.homehub.sources

import java.text.Normalizer
import java.util.Locale

/** Requête de recherche universelle. */
data class SearchQuery(
    val text: String,
    val language: String = "fr-FR",
    val region: String = "FR",
)

enum class ResultKind { MOVIE, SERIES, PERSON, APP }

/**
 * Disponibilité d'un contenu sur un service de streaming.
 * @property packages applications Android TV du service, par ordre de préférence.
 */
data class StreamingOffer(
    val service: String,
    val packages: List<String>,
)

/**
 * Résultat d'une source, avant mise en forme en carte.
 *
 * @property score pertinence (0..1+) utilisée pour le classement global.
 * @property packageName application concernée (résultats [ResultKind.APP]).
 */
data class SearchResult(
    val id: String,
    val kind: ResultKind,
    val title: String,
    val subtitle: String? = null,
    val overview: String? = null,
    val year: Int? = null,
    val imageUrl: String? = null,
    val offers: List<StreamingOffer> = emptyList(),
    val packageName: String? = null,
    val score: Double = 0.0,
    val source: String,
)

/** Une source de résultats (TMDB, applications installées, YouTube, Plex…). */
interface SearchSource {
    val id: String

    /** Vrai si la source est utilisable (clé configurée, service joignable…). */
    val isEnabled: Boolean get() = true

    suspend fun search(query: SearchQuery): List<SearchResult>
}

/** Application connue de la TV, vue par les sources. */
data class InstalledApp(
    val packageName: String,
    val label: String,
)

/** Normalisation commune aux sources : minuscules, sans accents ni ponctuation. */
internal fun String.normalized(): String =
    Normalizer.normalize(lowercase(Locale.FRENCH), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

/** Pertinence d'un titre pour une requête : 1 si identique, puis inclusion, puis mots communs. */
internal fun titleMatch(query: String, title: String): Double {
    val q = query.normalized()
    val t = title.normalized()
    if (q.isEmpty() || t.isEmpty()) return 0.0
    if (q == t) return 1.0
    if (t.startsWith(q) || q.startsWith(t)) return 0.8
    if (t.contains(q) || q.contains(t)) return 0.6
    val qWords = q.split(' ').toSet()
    val tWords = t.split(' ').toSet()
    val common = qWords.intersect(tWords).size
    return 0.5 * common / maxOf(qWords.size, 1)
}
