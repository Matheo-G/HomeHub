package com.homehub.sources

/**
 * Correspondance entre les services de streaming (noms TMDB/JustWatch) et
 * leurs applications Android TV, par ordre de préférence (version TV d'abord).
 */
object StreamingServices {

    private data class Service(val name: String, val aliases: List<String>, val packages: List<String>)

    private val services = listOf(
        Service(
            "Netflix",
            listOf("netflix"),
            listOf("com.netflix.ninja", "com.netflix.mediaclient"),
        ),
        Service(
            "Prime Video",
            listOf("amazon prime video", "prime video", "amazon video"),
            listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient", "com.amazon.avod"),
        ),
        Service(
            "Disney+",
            listOf("disney plus", "disney+"),
            listOf("com.disney.disneyplus"),
        ),
        Service(
            "Apple TV+",
            listOf("apple tv plus", "apple tv+"),
            listOf("com.apple.atve.androidtv.appletv", "com.apple.atve.amazon.appletv"),
        ),
        Service(
            "Canal+",
            listOf("canal+", "canal plus", "canal+ series"),
            listOf("com.canal.android.canal"),
        ),
        Service(
            "Max",
            listOf("max", "hbo max"),
            listOf("com.wbd.stream"),
        ),
        Service(
            "Paramount+",
            listOf("paramount plus", "paramount+"),
            listOf("com.cbs.ca", "com.cbs.app"),
        ),
        Service(
            "Plex",
            listOf("plex"),
            listOf("com.plexapp.android"),
        ),
    )

    /** Offre correspondant à un nom de service TMDB, ou `null` si inconnu. */
    fun resolve(providerName: String): StreamingOffer? {
        val key = providerName.lowercase().trim()
        val service = services.firstOrNull { service -> service.aliases.any { it == key } }
            ?: services.firstOrNull { service -> service.aliases.any { key.startsWith(it) } }
            ?: return null
        return StreamingOffer(service.name, service.packages)
    }
}
