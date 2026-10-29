package com.homehub.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Source TMDB (The Movie Database) : films, séries et personnes, avec leur
 * disponibilité par service de streaming dans le pays de l'utilisateur
 * (données JustWatch exposées par TMDB). Docs/adr/0007.
 *
 * Authentification : clé d'API v3 (paramètre `api_key`) ou jeton de lecture
 * v4 (en-tête `Authorization: Bearer`), au choix de l'utilisateur.
 *
 * Ce produit utilise l'API TMDB mais n'est ni approuvé ni certifié par TMDB.
 */
class TmdbSource(
    private val http: OkHttpClient,
    private val credential: () -> String?,
    private val baseUrl: String = "https://api.themoviedb.org/3/",
    private val imageBaseUrl: String = "https://image.tmdb.org/t/p/w185",
    private val maxResults: Int = 8,
    private val providerLookups: Int = 5,
) : SearchSource {

    override val id: String = "tmdb"

    override val isEnabled: Boolean get() = !credential().isNullOrBlank()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun search(query: SearchQuery): List<SearchResult> {
        val key = credential()?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val url = endpoint("search/multi", key) {
            addQueryParameter("query", query.text)
            addQueryParameter("language", query.language)
            addQueryParameter("region", query.region)
            addQueryParameter("include_adult", "false")
        }
        val page = json.decodeFromString(TmdbPage.serializer(), get(url, key))
        val items = page.results
            .filter { it.mediaType in SUPPORTED_TYPES }
            .sortedByDescending { it.popularity }
            .take(maxResults)

        // Disponibilité : uniquement pour les films et séries les plus pertinents.
        return coroutineScope {
            items.mapIndexed { index, item ->
                async {
                    val offers = if (index < providerLookups && item.mediaType != TYPE_PERSON) {
                        runCatching { providers(item, key, query.region) }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                    item.toResult(query, offers)
                }
            }.awaitAll()
        }
    }

    /** Teste la clé : `true` si TMDB l'accepte. */
    suspend fun verify(candidate: String): Boolean = runCatching {
        get(endpoint("configuration", candidate.trim()) {}, candidate.trim())
        true
    }.getOrDefault(false)

    private suspend fun providers(item: TmdbItem, key: String, region: String): List<StreamingOffer> {
        val url = endpoint("${item.mediaType}/${item.id}/watch/providers", key) {}
        val response = json.decodeFromString(TmdbProviders.serializer(), get(url, key))
        val country = response.results[region] ?: return emptyList()
        return (country.flatrate + country.free + country.ads)
            .mapNotNull { StreamingServices.resolve(it.providerName) }
            .distinctBy { it.service }
    }

    private fun TmdbItem.toResult(query: SearchQuery, offers: List<StreamingOffer>): SearchResult {
        val displayTitle = title ?: name ?: ""
        val date = releaseDate ?: firstAirDate
        val year = date?.take(4)?.toIntOrNull()
        val kind = when (mediaType) {
            TYPE_MOVIE -> ResultKind.MOVIE
            TYPE_TV -> ResultKind.SERIES
            else -> ResultKind.PERSON
        }
        val subtitle = when (kind) {
            ResultKind.MOVIE -> listOfNotNull(year?.toString(), "Film").joinToString(" · ")
            ResultKind.SERIES -> listOfNotNull(year?.toString(), "Série").joinToString(" · ")
            else -> listOfNotNull(
                department?.let(::departmentLabel),
                knownFor.mapNotNull { it.title ?: it.name }.take(2).joinToString(", ").ifBlank { null },
            ).joinToString(" · ")
        }
        val overview = when (kind) {
            ResultKind.PERSON -> knownFor.mapNotNull { it.title ?: it.name }.takeIf { it.isNotEmpty() }
                ?.let { "Connu pour : ${it.joinToString(", ")}" }
            else -> overview?.takeIf { it.isNotBlank() }
        }
        // Pertinence : correspondance du titre d'abord, popularité ensuite, bonus si disponible.
        val score = titleMatch(query.text, displayTitle) +
            (popularity / (popularity + POPULARITY_SCALE)) * 0.4 +
            (if (offers.isNotEmpty()) 0.1 else 0.0)
        return SearchResult(
            id = "tmdb:$mediaType:$id",
            kind = kind,
            title = displayTitle,
            subtitle = subtitle.ifBlank { null },
            overview = overview,
            year = year,
            imageUrl = (posterPath ?: profilePath)?.let { imageBaseUrl + it },
            offers = offers,
            score = score,
            source = this@TmdbSource.id,
        )
    }

    private fun departmentLabel(department: String): String = when (department) {
        "Directing" -> "Réalisation"
        "Acting" -> "Interprétation"
        "Writing" -> "Scénario"
        "Production" -> "Production"
        "Sound" -> "Musique"
        else -> department
    }

    private fun endpoint(path: String, key: String, query: HttpUrl.Builder.() -> Unit): HttpUrl {
        val builder = (baseUrl + path).toHttpUrl().newBuilder()
        if (!key.isBearerToken()) builder.addQueryParameter("api_key", key)
        builder.query()
        return builder.build()
    }

    private suspend fun get(url: HttpUrl, key: String): String {
        val request = Request.Builder().url(url).apply {
            if (key.isBearerToken()) header("Authorization", "Bearer $key")
            header("Accept", "application/json")
        }.build()
        return withContext(Dispatchers.IO) {
            http.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw IOException("TMDB : HTTP ${response.code}")
                response.body?.string() ?: throw IOException("TMDB : réponse vide")
            }
        }
    }

    /** Les jetons v4 sont des JWT (trois segments séparés par des points). */
    private fun String.isBearerToken(): Boolean = count { it == '.' } == 2

    // --- Modèles de réponse TMDB ---------------------------------------------

    @Serializable
    private data class TmdbPage(val results: List<TmdbItem> = emptyList())

    @Serializable
    private data class TmdbItem(
        val id: Long,
        @SerialName("media_type") val mediaType: String = "",
        val title: String? = null,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("profile_path") val profilePath: String? = null,
        val popularity: Double = 0.0,
        @SerialName("known_for_department") val department: String? = null,
        @SerialName("known_for") val knownFor: List<TmdbKnownFor> = emptyList(),
    )

    @Serializable
    private data class TmdbKnownFor(val title: String? = null, val name: String? = null)

    @Serializable
    private data class TmdbProviders(val results: Map<String, TmdbCountry> = emptyMap())

    @Serializable
    private data class TmdbCountry(
        val flatrate: List<TmdbProvider> = emptyList(),
        val free: List<TmdbProvider> = emptyList(),
        val ads: List<TmdbProvider> = emptyList(),
    )

    @Serializable
    private data class TmdbProvider(@SerialName("provider_name") val providerName: String = "")

    private companion object {
        const val TYPE_MOVIE = "movie"
        const val TYPE_TV = "tv"
        const val TYPE_PERSON = "person"
        val SUPPORTED_TYPES = setOf(TYPE_MOVIE, TYPE_TV, TYPE_PERSON)
        const val POPULARITY_SCALE = 20.0
    }
}

/** Appel OkHttp suspendu et annulable. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        },
    )
}
