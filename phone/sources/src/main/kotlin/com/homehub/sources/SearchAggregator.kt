package com.homehub.sources

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Applications installées sur la TV dont le nom correspond à la requête
 * (« Recherche Plex » doit proposer l'application Plex).
 */
class InstalledAppsSource(
    private val apps: () -> List<InstalledApp>,
) : SearchSource {

    override val id: String = "apps"

    override suspend fun search(query: SearchQuery): List<SearchResult> =
        apps().mapNotNull { app ->
            val match = titleMatch(query.text, app.label)
            if (match < MIN_MATCH) return@mapNotNull null
            SearchResult(
                id = "app:${app.packageName}",
                kind = ResultKind.APP,
                title = app.label,
                subtitle = "Application",
                packageName = app.packageName,
                // Un nom d'application exact passe devant tout le reste.
                score = match * 1.5,
                source = id,
            )
        }

    private companion object {
        const val MIN_MATCH = 0.6
    }
}

/**
 * Interroge toutes les sources actives **en parallèle**, chacune bornée par
 * [timeoutMs] : une source lente ou en erreur ne retarde ni ne fait échouer
 * la recherche. Les résultats sont dédupliqués puis classés par pertinence.
 */
class SearchAggregator(
    private val sources: List<SearchSource>,
    private val timeoutMs: Long = 2_500,
    private val maxResults: Int = 16,
) {
    /** Résultat global, avec la liste des sources qui n'ont pas répondu. */
    data class Outcome(val results: List<SearchResult>, val failedSources: List<String>)

    suspend fun search(query: SearchQuery): Outcome = coroutineScope {
        val answers = sources.filter { it.isEnabled }.map { source ->
            async {
                val results = withTimeoutOrNull(timeoutMs) {
                    try {
                        source.search(query)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        null
                    }
                }
                source.id to results
            }
        }.awaitAll()

        val merged = answers.flatMap { it.second.orEmpty() }
        Outcome(
            results = deduplicate(merged).sortedByDescending { it.score }.take(maxResults),
            failedSources = answers.filter { it.second == null }.map { it.first },
        )
    }

    /** Même contenu venant de deux sources : on garde le plus pertinent, offres fusionnées. */
    private fun deduplicate(results: List<SearchResult>): List<SearchResult> =
        results.groupBy { "${it.kind}|${it.title.normalized()}|${it.year ?: ""}" }
            .values
            .map { group ->
                val best = group.maxBy { it.score }
                best.copy(offers = group.flatMap { it.offers }.distinctBy { it.service })
            }
}
