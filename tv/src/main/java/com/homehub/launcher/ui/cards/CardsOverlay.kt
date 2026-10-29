package com.homehub.launcher.ui.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.homehub.launcher.cards.DisplayedSurface
import com.homehub.launcher.ui.components.AppArtwork
import com.homehub.launcher.ui.components.FocusableSurface
import com.homehub.launcher.ui.components.SectionTitle
import com.homehub.launcher.ui.components.awaitLayout
import com.homehub.launcher.ui.theme.HubColors
import com.homehub.launcher.ui.theme.HubDimens
import com.homehub.launcher.ui.theme.LocalAccent
import com.homehub.protocol.Card
import com.homehub.protocol.CardKind
import com.homehub.protocol.ImageRatio
import com.homehub.protocol.SurfaceState
import kotlinx.coroutines.delay

/**
 * Moteur de rendu des cartes (docs/adr/0008) : résultats de recherche,
 * et demain réponses IA et widgets en grand.
 *
 * Disposition « results » : en-tête (requête, état), panneau de détail de la
 * carte focalisée, puis une rangée horizontale par section.
 * OK active l'action principale de la carte ; Retour ferme la surface.
 */
@Composable
fun CardsOverlay(
    displayed: DisplayedSurface,
    onAction: (cardId: String, actionId: String) -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = displayed.surface
    var focusedCard by remember(surface.id) { mutableStateOf<Card?>(null) }
    val firstCardFocus = remember(surface.id) { FocusRequester() }
    val firstCardId = surface.cards.firstOrNull()?.id

    // Focus initial sur la première carte dès qu'elle existe.
    LaunchedEffect(surface.id, firstCardId) {
        if (firstCardId != null) {
            awaitLayout()
            runCatching { firstCardFocus.requestFocus() }
        }
    }
    // Fermeture automatique sans interaction (délai relancé à chaque mise à jour ou déplacement).
    val ttl = surface.ttlMs
    LaunchedEffect(displayed.revision, focusedCard?.id, ttl) {
        if (ttl != null && ttl > 0) {
            delay(ttl)
            onTimeout()
        }
    }

    val sections = remember(surface.cards) {
        surface.cards.groupBy { it.section ?: defaultSection(it.kind) }.toList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.6f))))
            .padding(top = HubDimens.ScreenTop),
    ) {
        Header(
            title = surface.title,
            subtitle = surface.subtitle,
            state = surface.state,
            modifier = Modifier.padding(horizontal = HubDimens.ScreenHorizontal),
        )
        Spacer(Modifier.height(14.dp))
        DetailPanel(
            card = focusedCard ?: surface.cards.firstOrNull(),
            modifier = Modifier.padding(horizontal = HubDimens.ScreenHorizontal),
        )

        when {
            surface.cards.isEmpty() && surface.state == SurfaceState.LOADING -> LoadingRow()
            surface.cards.isEmpty() -> Text(
                text = "Aucun résultat",
                style = MaterialTheme.typography.titleMedium,
                color = HubColors.TextTertiary,
                modifier = Modifier.padding(horizontal = HubDimens.ScreenHorizontal, vertical = 24.dp),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                sections.forEach { (title, cards) ->
                    item(key = "section:$title") {
                        Column {
                            SectionTitle(title, Modifier.padding(start = HubDimens.ScreenHorizontal, top = 6.dp))
                            LazyRow(
                                contentPadding = PaddingValues(
                                    horizontal = HubDimens.ScreenHorizontal,
                                    vertical = HubDimens.FocusBleed,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                items(cards, key = { it.id }) { card ->
                                    CardTile(
                                        card = card,
                                        image = displayed.images[card.id],
                                        focusRequester = if (card.id == firstCardId) firstCardFocus else null,
                                        onFocused = { focusedCard = card },
                                        onClick = {
                                            card.actions.firstOrNull()?.let { action -> onAction(card.id, action.id) }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String?, state: SurfaceState, modifier: Modifier) {
    Column(modifier) {
        SectionTitle("Recherche")
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = HubColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AnimatedContent(
            targetState = if (state == SurfaceState.LOADING) null else subtitle,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "subtitle",
        ) { text ->
            Text(
                text = text ?: "Recherche en cours…",
                style = MaterialTheme.typography.bodyMedium,
                color = HubColors.TextTertiary,
            )
        }
    }
}

/** Détail de la carte focalisée : titre, informations, résumé, services, action. */
@Composable
private fun DetailPanel(card: Card?, modifier: Modifier) {
    Column(modifier = modifier.heightIn(min = 96.dp)) {
        if (card == null) return@Column
        val accent = LocalAccent.current.value
        Text(
            text = card.title,
            style = MaterialTheme.typography.headlineSmall,
            color = HubColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            card.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = HubColors.TextSecondary, maxLines = 1)
            }
            card.badges.forEach { badge ->
                Spacer(Modifier.width(10.dp))
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = HubColors.TextPrimary,
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        card.body?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = HubColors.TextTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
        }
        card.actions.firstOrNull()?.let { action ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = "OK · ${action.label}",
                style = MaterialTheme.typography.titleSmall,
                color = accent,
            )
        }
    }
}

/** Carte focalisable : image au bon ratio, titre en dessous. */
@Composable
private fun CardTile(
    card: Card,
    image: ImageBitmap?,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val (width, height) = tileSize(card.image?.ratio ?: defaultRatio(card.kind))
    Column(modifier = Modifier.width(width)) {
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier.size(width, height),
            cornerRadius = 14.dp,
            focusRequester = focusRequester,
            onFocusChange = { if (it) onFocused() },
        ) {
            val appPackage = card.image?.app
            when {
                image != null -> Image(
                    bitmap = image,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize(),
                )
                appPackage != null -> AppArtwork(packageName = appPackage, title = card.title, modifier = Modifier.fillMaxSize())
                else -> Placeholder(card)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = card.title,
            style = MaterialTheme.typography.bodyMedium,
            color = HubColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        card.subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = HubColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Carte sans image (image en cours d'envoi, ou absente) : initiale sur un dégradé. */
@Composable
private fun Placeholder(card: Card) {
    val accent = LocalAccent.current.value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.25f), HubColors.CardRaised))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = card.title.take(1).uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            color = HubColors.TextTertiary,
        )
    }
}

/** Cartes fantômes pendant la recherche. */
@Composable
private fun LoadingRow() {
    val transition = rememberInfiniteTransition(label = "loading")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Row(
        modifier = Modifier.padding(horizontal = HubDimens.ScreenHorizontal, vertical = HubDimens.FocusBleed),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        repeat(6) {
            val (width, height) = tileSize(ImageRatio.POSTER)
            Box(
                Modifier
                    .size(width, height)
                    .graphicsLayer { alpha = pulse }
                    .background(HubColors.CardRaised, RoundedCornerShape(14.dp)),
            )
        }
    }
}

private fun tileSize(ratio: ImageRatio): Pair<Dp, Dp> = when (ratio) {
    ImageRatio.POSTER -> 112.dp to 168.dp
    ImageRatio.LANDSCAPE -> 192.dp to 108.dp
    ImageRatio.SQUARE -> 132.dp to 132.dp
}

private fun defaultRatio(kind: CardKind): ImageRatio = when (kind) {
    CardKind.VIDEO, CardKind.PERSON -> ImageRatio.POSTER
    CardKind.APP, CardKind.WEB -> ImageRatio.LANDSCAPE
    else -> ImageRatio.SQUARE
}

private fun defaultSection(kind: CardKind): String = when (kind) {
    CardKind.VIDEO -> "Films et séries"
    CardKind.PERSON -> "Personnes"
    CardKind.APP -> "Applications"
    CardKind.MUSIC -> "Musique"
    CardKind.WEB -> "Web"
    else -> "Résultats"
}
