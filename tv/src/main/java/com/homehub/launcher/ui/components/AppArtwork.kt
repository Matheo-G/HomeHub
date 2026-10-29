package com.homehub.launcher.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.homehub.launcher.data.models.Artwork
import com.homehub.launcher.ui.theme.HubColors

/**
 * Accès aux visuels des applications depuis l'UI.
 *
 * Interface plutôt que repository concret : les composants restent testables
 * et prévisualisables avec un chargeur factice.
 */
interface ArtworkLoader {
    /** Visuel déjà en cache, sans travail. */
    fun peek(packageName: String): Artwork?

    /** Charge le visuel hors du thread principal. */
    suspend fun load(packageName: String): Artwork?
}

/** Chargeur par défaut (prévisualisations) : aucun visuel. */
private object EmptyArtworkLoader : ArtworkLoader {
    override fun peek(packageName: String): Artwork? = null
    override suspend fun load(packageName: String): Artwork? = null
}

val LocalArtworkLoader = staticCompositionLocalOf<ArtworkLoader> { EmptyArtworkLoader }

/**
 * Visuel d'une application, chargé en arrière-plan. Le cache est consulté en
 * premier : une carte déjà vue s'affiche instantanément, sans fondu.
 */
@Composable
fun rememberArtwork(packageName: String?): State<Artwork?> {
    val loader = LocalArtworkLoader.current
    return produceState(initialValue = packageName?.let(loader::peek), packageName) {
        value = if (packageName == null) null else loader.peek(packageName) ?: loader.load(packageName)
    }
}

/**
 * Contenu visuel d'une carte d'application :
 *  - bannière TV 16:9 en plein cadre si l'application en fournit une ;
 *  - sinon son icône centrée sur un dégradé sombre ;
 *  - pendant le chargement, l'initiale du nom.
 */
@Composable
fun AppArtwork(
    packageName: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val artwork by rememberArtwork(packageName)
    Crossfade(
        targetState = artwork,
        animationSpec = tween(durationMillis = 220),
        modifier = modifier,
        label = "artwork",
    ) { current ->
        when {
            current == null -> ArtworkPlaceholder(title)
            current.isBanner -> Image(
                bitmap = current.bitmap,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium,
            )
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(IconBackdrop),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = current.bitmap,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxHeight(0.5f)
                        .aspectRatio(1f),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }
}

@Composable
private fun ArtworkPlaceholder(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IconBackdrop),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.take(1).uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            color = HubColors.TextTertiary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(8.dp),
        )
    }
}

/** Dégradé derrière les icônes non-bannières. */
private val IconBackdrop = Brush.verticalGradient(
    colors = listOf(Color(0xFF232836), Color(0xFF161A22)),
)
