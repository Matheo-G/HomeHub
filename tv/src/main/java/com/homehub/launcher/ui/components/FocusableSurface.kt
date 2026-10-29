package com.homehub.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homehub.launcher.ui.theme.HubColors
import com.homehub.launcher.ui.theme.HubMotion
import com.homehub.launcher.ui.theme.LocalAccent

/**
 * Surface focalisable à la télécommande : brique de base de toutes les cartes.
 *
 * Au focus, la carte :
 *  - s'agrandit sur un ressort doux et se soulève légèrement ;
 *  - projette une ombre plus profonde ;
 *  - s'entoure d'un halo et d'un contour lumineux de la couleur d'accent ;
 *  - reçoit un reflet diagonal qui suggère une surface vitrée.
 *
 * Performance : la progression du focus est une valeur animée lue uniquement
 * dans `graphicsLayer` et dans les blocs de dessin. Pendant l'animation, seul
 * le calque est redessiné ; l'arbre Compose n'est recomposé qu'une fois, au
 * changement de focus. Le halo est une superposition de contours translucides
 * plutôt qu'un flou GPU (indisponible avant Android 12).
 *
 * @param onClick action au clic (OK / centre de la croix directionnelle).
 * @param cornerRadius rayon des coins, aussi utilisé par le halo.
 * @param containerColor couleur de fond de la carte.
 * @param focusedScale facteur d'agrandissement au focus.
 * @param focusRequester permet de donner le focus à cette carte par programme.
 * @param onFocusChange notifié à chaque gain ou perte de focus.
 */
@Composable
fun FocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    containerColor: Color = HubColors.Card,
    focusedScale: Float = HubMotion.FocusScale,
    focusRequester: FocusRequester? = null,
    onFocusChange: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = LocalAccent.current
    val interactionSource = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    val isPressed by interactionSource.collectIsPressedAsState()

    val focusProgress = animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = HubMotion.FocusSpring,
        label = "focus",
    )
    val pressScale = animateFloatAsState(
        targetValue = if (isPressed) HubMotion.PressScale else 1f,
        animationSpec = HubMotion.PressSpring,
        label = "press",
    )
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { state ->
                if (state.isFocused != isFocused) {
                    isFocused = state.isFocused
                    onFocusChange(state.isFocused)
                }
            }
            .graphicsLayer {
                val progress = focusProgress.value
                val scale = (1f + (focusedScale - 1f) * progress) * pressScale.value
                scaleX = scale
                scaleY = scale
                translationY = -LiftDistance.toPx() * progress
                shadowElevation = RestElevation.toPx() + (FocusElevation - RestElevation).toPx() * progress
                this.shape = shape
                clip = false
            }
            .drawBehind {
                drawFocusHalo(
                    color = accent.value,
                    progress = focusProgress.value,
                    cornerRadiusPx = cornerRadius.toPx(),
                )
            }
            .clip(shape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .drawWithContent {
                drawContent()
                drawFocusOverlay(
                    accent = accent.value,
                    progress = focusProgress.value,
                    cornerRadiusPx = cornerRadius.toPx(),
                )
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Soulèvement maximal au focus. */
private val LiftDistance = 6.dp

/** Ombre au repos puis au focus. */
private val RestElevation = 2.dp
private val FocusElevation = 22.dp

/** Nombre d'anneaux du halo et leur écart. */
private const val HaloRings = 5
private val HaloRingSpacing = 3.dp

/**
 * Halo : anneaux concentriques de plus en plus transparents autour de la
 * carte, dessinés hors de ses limites (le calque n'est pas rogné).
 */
private fun DrawScope.drawFocusHalo(color: Color, progress: Float, cornerRadiusPx: Float) {
    if (progress <= 0.01f) return
    val spacing = HaloRingSpacing.toPx()
    val stroke = Stroke(width = spacing)
    for (ring in 1..HaloRings) {
        val spread = ring * spacing
        val fade = 1f - (ring - 1).toFloat() / HaloRings
        drawRoundRect(
            color = color.copy(alpha = 0.18f * fade * progress),
            topLeft = Offset(-spread, -spread),
            size = Size(size.width + spread * 2, size.height + spread * 2),
            cornerRadius = CornerRadius(cornerRadiusPx + spread),
            style = stroke,
        )
    }
}

/**
 * Premier plan : fin liseré au repos, puis reflet diagonal et contour
 * lumineux qui apparaissent avec le focus.
 */
private fun DrawScope.drawFocusOverlay(accent: Color, progress: Float, cornerRadiusPx: Float) {
    val restStroke = 1.dp.toPx()
    val focusStroke = 2.5.dp.toPx()

    if (progress > 0.01f) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.16f * progress), Color.Transparent),
                start = Offset.Zero,
                end = Offset(size.width * 0.75f, size.height),
            ),
        )
    }

    val strokeWidth = restStroke + (focusStroke - restStroke) * progress
    val inset = strokeWidth / 2f
    val outlineColor = if (progress > 0.01f) {
        accent.copy(alpha = 0.25f + 0.75f * progress)
    } else {
        HubColors.CardOutline
    }
    drawRoundRect(
        color = outlineColor,
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = CornerRadius((cornerRadiusPx - inset).coerceAtLeast(0f)),
        style = Stroke(width = strokeWidth),
    )
}
