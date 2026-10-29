package com.homehub.remote.planner

import com.homehub.protocol.AppEntry
import com.homehub.protocol.Card
import com.homehub.protocol.CardAction
import com.homehub.protocol.CardActionEvent
import com.homehub.protocol.CardImage
import com.homehub.protocol.CardKind
import com.homehub.protocol.CardPatch
import com.homehub.protocol.CardSurface
import com.homehub.protocol.CardsDismissParams
import com.homehub.protocol.CardsDismissedEvent
import com.homehub.protocol.CardsShowParams
import com.homehub.protocol.CardsUpdateParams
import com.homehub.protocol.Events
import com.homehub.protocol.ImageRatio
import com.homehub.protocol.JsonRpc
import com.homehub.protocol.LaunchParams
import com.homehub.protocol.Methods
import com.homehub.protocol.OpenIntent
import com.homehub.protocol.OpenParams
import com.homehub.protocol.RpcMessage
import com.homehub.protocol.SurfaceLayout
import com.homehub.protocol.SurfaceState
import com.homehub.protocol.ToastKind
import com.homehub.protocol.ToastParams
import com.homehub.remote.data.RemoteClient
import com.homehub.sources.ResultKind
import com.homehub.sources.SearchAggregator
import com.homehub.sources.SearchQuery
import com.homehub.sources.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Recherche universelle (docs/adr/0007) : interroge les sources sur le
 * téléphone, met les résultats en cartes, les affiche sur la TV, puis
 * décide quoi faire quand l'utilisateur active une carte.
 *
 * Déroulé de « Recherche Interstellar » :
 *  1. `cards.show` (état « loading ») — la TV affiche aussitôt la recherche ;
 *  2. sources interrogées en parallèle (≤ 2,5 s) ;
 *  3. `cards.show` avec les résultats, puis une `cards.update` par affiche
 *     (images envoyées par le téléphone : la TV n'accède pas à Internet) ;
 *  4. événement `card.action` → ouverture dans la bonne application.
 */
class SearchCoordinator(
    private val client: RemoteClient,
    private val aggregator: SearchAggregator,
    private val http: OkHttpClient,
    private val installedApps: () -> List<AppEntry>,
) {
    /** Action à exécuter pour chaque couple (carte, action) de la surface affichée. */
    private val handlers = ConcurrentHashMap<String, suspend () -> Outcome>()
    private val counter = AtomicInteger()

    @Volatile
    private var currentSurface: String? = null

    suspend fun search(query: String): Outcome {
        val surfaceId = "search-${counter.incrementAndGet()}"
        handlers.clear()
        currentSurface = surfaceId
        client.call(
            Methods.CardsShow,
            CardsShowParams(
                CardSurface(id = surfaceId, layout = SurfaceLayout.RESULTS, title = query, state = SurfaceState.LOADING),
            ),
        )

        val outcome = aggregator.search(SearchQuery(query))
        val installed = installedApps().associateBy { it.packageName }
        val resultCards = outcome.results.map { result -> toCard(result, installed) }
        val inAppCards = searchInAppCards(query, installed)
        val cards = resultCards.map { it.first } + inAppCards

        val count = resultCards.size
        val subtitle = when {
            count == 0 && "tmdb" in outcome.failedSources -> "TMDB indisponible : recherche dans vos applications"
            count == 0 -> "Choisissez où chercher"
            count == 1 -> "1 résultat"
            else -> "$count résultats"
        }
        client.call(
            Methods.CardsShow,
            CardsShowParams(
                CardSurface(
                    id = surfaceId,
                    layout = SurfaceLayout.RESULTS,
                    title = query,
                    subtitle = subtitle,
                    state = SurfaceState.DONE,
                    cards = cards,
                    ttlMs = SURFACE_TTL_MS,
                ),
            ),
        )
        sendPosters(surfaceId, resultCards)
        return Outcome(true, if (count > 0) "$subtitle sur la TV" else "Recherche affichée sur la TV")
    }

    /** Événements de la TV (`card.action`, `cards.dismissed`). */
    suspend fun onEvent(event: RpcMessage.Call) {
        when (event.method) {
            Events.CardAction.name -> {
                val action = runCatching { JsonRpc.decodeParams(Events.CardAction.params, event.params) }.getOrNull() ?: return
                handle(action)
            }
            Events.CardsDismissed.name -> {
                val dismissed = runCatching { JsonRpc.decodeParams(Events.CardsDismissed.params, event.params) }.getOrNull()
                forget(dismissed)
            }
        }
    }

    private suspend fun handle(action: CardActionEvent) {
        if (action.surfaceId != currentSurface) return
        val handler = handlers[key(action.cardId, action.actionId)] ?: return
        val outcome = runCatching { handler() }.getOrElse { Outcome(false, it.message ?: "Action impossible") }
        if (outcome.ok) {
            runCatching { client.call(Methods.CardsDismiss, CardsDismissParams(action.surfaceId)) }
            handlers.clear()
            currentSurface = null
        }
        client.notify(
            Methods.UiToast,
            ToastParams(outcome.message, kind = if (outcome.ok) ToastKind.SUCCESS else ToastKind.ERROR),
        )
    }

    private fun forget(event: CardsDismissedEvent?) {
        if (event == null || event.surfaceId == currentSurface) {
            handlers.clear()
            currentSurface = null
        }
    }

    // --- Mise en cartes --------------------------------------------------------

    private fun toCard(result: SearchResult, installed: Map<String, AppEntry>): Pair<Card, SearchResult> {
        val actions = mutableListOf<CardAction>()
        val badges = result.offers.map { it.service }
        when (result.kind) {
            ResultKind.MOVIE, ResultKind.SERIES -> {
                // Premier service disponible et installé : on y cherche le titre.
                val target = result.offers.firstNotNullOfOrNull { offer ->
                    offer.packages.firstOrNull { it in installed }?.let { offer.service to it }
                }
                if (target != null) {
                    actions += CardAction(ACTION_WATCH, "Regarder sur ${target.first}")
                    register(result.id, ACTION_WATCH) { searchIn(target.second, target.first, result.title) }
                } else {
                    youtube(installed)?.let { youtube ->
                        actions += CardAction(ACTION_TRAILER, "Bande-annonce sur YouTube")
                        register(result.id, ACTION_TRAILER) { searchIn(youtube.packageName, "YouTube", "${result.title} bande-annonce") }
                    }
                }
            }
            ResultKind.PERSON -> youtube(installed)?.let { youtube ->
                actions += CardAction(ACTION_YOUTUBE, "Vidéos sur YouTube")
                register(result.id, ACTION_YOUTUBE) { searchIn(youtube.packageName, "YouTube", result.title) }
            }
            ResultKind.APP -> result.packageName?.let { packageName ->
                actions += CardAction(ACTION_OPEN, "Ouvrir")
                register(result.id, ACTION_OPEN) {
                    client.call(Methods.AppLaunch, LaunchParams(packageName))
                    Outcome(true, "Ouverture de ${result.title}")
                }
            }
        }
        val card = Card(
            id = result.id,
            kind = when (result.kind) {
                ResultKind.MOVIE, ResultKind.SERIES -> CardKind.VIDEO
                ResultKind.PERSON -> CardKind.PERSON
                ResultKind.APP -> CardKind.APP
            },
            title = result.title,
            subtitle = result.subtitle,
            body = result.overview,
            image = when (result.kind) {
                ResultKind.APP -> CardImage(app = result.packageName, ratio = ImageRatio.LANDSCAPE)
                else -> CardImage(ratio = ImageRatio.POSTER)
            },
            badges = badges,
            actions = actions,
            section = when (result.kind) {
                ResultKind.MOVIE, ResultKind.SERIES -> "Films et séries"
                ResultKind.PERSON -> "Personnes"
                ResultKind.APP -> "Applications"
            },
        )
        return card to result
    }

    /** « Chercher « Interstellar » dans… » : une carte par service de recherche installé. */
    private fun searchInAppCards(query: String, installed: Map<String, AppEntry>): List<Card> =
        SEARCHABLE_APPS.mapNotNull { (label, packages) ->
            val packageName = packages.firstOrNull { it in installed } ?: return@mapNotNull null
            val id = "in:$packageName"
            register(id, ACTION_SEARCH) { searchIn(packageName, label, query) }
            Card(
                id = id,
                kind = CardKind.APP,
                title = label,
                subtitle = "Chercher « $query »",
                image = CardImage(app = packageName, ratio = ImageRatio.LANDSCAPE),
                actions = listOf(CardAction(ACTION_SEARCH, "Chercher dans $label")),
                section = "Chercher dans…",
            )
        }

    private suspend fun searchIn(packageName: String, label: String, query: String): Outcome {
        val result = client.call(Methods.AppOpen, OpenParams(packageName, OpenIntent.SEARCH, query = query))
        return if (result.fallback == null) {
            Outcome(true, "Recherche de « $query » sur $label")
        } else {
            Outcome(true, "$label ouvert : lancez la recherche depuis l'application")
        }
    }

    /** Affiches téléchargées par le téléphone puis envoyées une à une (≤ 60 Ko chacune). */
    private suspend fun sendPosters(surfaceId: String, cards: List<Pair<Card, SearchResult>>) = coroutineScope {
        val permits = Semaphore(POSTER_PARALLELISM)
        cards.filter { it.second.imageUrl != null }.map { (card, result) ->
            async {
                permits.withPermit {
                    val data = download(result.imageUrl!!) ?: return@withPermit
                    if (currentSurface != surfaceId) return@withPermit
                    runCatching {
                        client.call(
                            Methods.CardsUpdate,
                            CardsUpdateParams(
                                surfaceId,
                                CardPatch(replaceCards = listOf(card.copy(image = card.image?.copy(data = data)))),
                            ),
                        )
                    }
                }
            }
        }.awaitAll()
    }

    private suspend fun download(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val bytes = response.body?.bytes()?.takeIf { response.isSuccessful && it.size <= MAX_IMAGE_BYTES }
                bytes?.let { Base64.getEncoder().encodeToString(it) }
            }
        }.getOrNull()
    }

    private fun youtube(installed: Map<String, AppEntry>): AppEntry? =
        YOUTUBE_PACKAGES.firstNotNullOfOrNull { installed[it] }

    private fun register(cardId: String, actionId: String, handler: suspend () -> Outcome) {
        handlers[key(cardId, actionId)] = handler
    }

    private fun key(cardId: String, actionId: String) = "$cardId/$actionId"

    private companion object {
        const val SURFACE_TTL_MS = 120_000L
        const val POSTER_PARALLELISM = 3
        const val MAX_IMAGE_BYTES = 60 * 1024

        const val ACTION_WATCH = "watch"
        const val ACTION_TRAILER = "trailer"
        const val ACTION_YOUTUBE = "youtube"
        const val ACTION_OPEN = "open"
        const val ACTION_SEARCH = "search"

        val YOUTUBE_PACKAGES = listOf(
            "com.google.android.youtube.tv",
            "com.google.android.youtube.googletv",
            "com.google.android.youtube",
        )

        /** Services où l'on peut chercher directement, par ordre d'affichage. */
        val SEARCHABLE_APPS = listOf(
            "Netflix" to listOf("com.netflix.ninja", "com.netflix.mediaclient"),
            "YouTube" to YOUTUBE_PACKAGES,
            "Prime Video" to listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
            "Plex" to listOf("com.plexapp.android"),
            "Disney+" to listOf("com.disney.disneyplus"),
        )
    }
}
