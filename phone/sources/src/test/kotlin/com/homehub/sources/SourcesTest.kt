package com.homehub.sources

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Sources de recherche : TMDB (serveur simulé), applications, agrégation. */
class SourcesTest {

    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                return when {
                    path.endsWith("/search/multi") -> json(SEARCH)
                    path.endsWith("/movie/157336/watch/providers") -> json(PROVIDERS)
                    path.endsWith("/watch/providers") -> json("""{"results":{}}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun tmdb(key: String? = "test-key") = TmdbSource(
        http = OkHttpClient(),
        credential = { key },
        baseUrl = server.url("/3/").toString(),
        imageBaseUrl = "https://img/w185",
    )

    @Test
    fun tmdbParsesMoviesPeopleAndStreamingOffers() = runTest {
        val results = tmdb().search(SearchQuery("Interstellar"))
        val movie = results.first { it.kind == ResultKind.MOVIE }
        assertEquals("Interstellar", movie.title)
        assertEquals(2014, movie.year)
        assertEquals("https://img/w185/poster.jpg", movie.imageUrl)
        assertEquals(listOf("Netflix", "Prime Video"), movie.offers.map { it.service })
        assertEquals("com.netflix.ninja", movie.offers.first().packages.first())

        val person = results.first { it.kind == ResultKind.PERSON }
        assertEquals("Christopher Nolan", person.title)
        assertTrue(person.overview!!.contains("Inception"))
    }

    @Test
    fun tmdbUsesApiKeyParameterOrBearerToken() = runTest {
        tmdb("v3key").search(SearchQuery("Interstellar"))
        assertEquals("v3key", server.takeRequest().requestUrl?.queryParameter("api_key"))

        tmdb("aaa.bbb.ccc").search(SearchQuery("Interstellar"))
        val requests = (1..server.requestCount - 1).map { server.takeRequest() }
        val bearer = requests.first { it.requestUrl!!.encodedPath.endsWith("/search/multi") && it.getHeader("Authorization") != null }
        assertEquals("Bearer aaa.bbb.ccc", bearer.getHeader("Authorization"))
    }

    @Test
    fun tmdbIsDisabledWithoutKey() = runTest {
        val source = tmdb(key = null)
        assertEquals(false, source.isEnabled)
        assertTrue(source.search(SearchQuery("Interstellar")).isEmpty())
    }

    @Test
    fun installedAppsMatchByName() = runTest {
        val source = InstalledAppsSource { listOf(InstalledApp("com.plexapp.android", "Plex"), InstalledApp("a.b", "Netflix")) }
        val results = source.search(SearchQuery("plex"))
        assertEquals(listOf("com.plexapp.android"), results.map { it.packageName })
    }

    @Test
    fun aggregatorSurvivesSlowAndFailingSources() = runTest {
        val slow = object : SearchSource {
            override val id = "slow"
            override suspend fun search(query: SearchQuery): List<SearchResult> {
                delay(10_000)
                return emptyList()
            }
        }
        val broken = object : SearchSource {
            override val id = "broken"
            override suspend fun search(query: SearchQuery): List<SearchResult> = error("panne")
        }
        val apps = InstalledAppsSource { listOf(InstalledApp("com.plexapp.android", "Plex")) }
        val outcome = SearchAggregator(listOf(slow, broken, apps), timeoutMs = 500).search(SearchQuery("Plex"))
        assertEquals(1, outcome.results.size)
        assertEquals(setOf("slow", "broken"), outcome.failedSources.toSet())
    }

    @Test
    fun aggregatorDeduplicatesAndMergesOffers() = runTest {
        fun source(name: String, offer: String) = object : SearchSource {
            override val id = name
            override suspend fun search(query: SearchQuery) = listOf(
                SearchResult(
                    id = "$name:1",
                    kind = ResultKind.MOVIE,
                    title = "Interstellar",
                    year = 2014,
                    offers = listOf(StreamingOffer(offer, emptyList())),
                    score = if (name == "a") 0.9 else 0.5,
                    source = name,
                ),
            )
        }
        val outcome = SearchAggregator(listOf(source("a", "Netflix"), source("b", "Plex"))).search(SearchQuery("Interstellar"))
        assertEquals(1, outcome.results.size)
        assertEquals("a:1", outcome.results.single().id)
        assertEquals(setOf("Netflix", "Plex"), outcome.results.single().offers.map { it.service }.toSet())
    }

    @Test
    fun streamingServicesAreResolvedByName() {
        assertEquals("Prime Video", StreamingServices.resolve("Amazon Prime Video")?.service)
        assertEquals("Netflix", StreamingServices.resolve("Netflix basic with Ads")?.service)
        assertEquals(null, StreamingServices.resolve("Service inconnu"))
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private companion object {
        val SEARCH = """
            {"page":1,"results":[
              {"id":157336,"media_type":"movie","title":"Interstellar","overview":"Des explorateurs…",
               "release_date":"2014-11-05","poster_path":"/poster.jpg","popularity":120.5},
              {"id":525,"media_type":"person","name":"Christopher Nolan","known_for_department":"Directing",
               "profile_path":"/nolan.jpg","popularity":30.0,
               "known_for":[{"title":"Inception"},{"title":"Interstellar"}]},
              {"id":9,"media_type":"collection","name":"Ignorée"}
            ]}
        """.trimIndent()

        val PROVIDERS = """
            {"id":157336,"results":{"FR":{"flatrate":[
              {"provider_id":8,"provider_name":"Netflix"},
              {"provider_id":119,"provider_name":"Amazon Prime Video"},
              {"provider_id":999,"provider_name":"Service inconnu"}
            ]},"US":{"flatrate":[{"provider_id":15,"provider_name":"Hulu"}]}}}
        """.trimIndent()
    }
}
