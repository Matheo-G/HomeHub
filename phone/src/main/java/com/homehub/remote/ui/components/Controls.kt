package com.homehub.remote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homehub.protocol.RemoteKey
import com.homehub.remote.ui.theme.RemoteColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/** Délai avant répétition d'une touche maintenue, puis intervalle de répétition. */
private const val REPEAT_START_MS = 420L
private const val REPEAT_INTERVAL_MS = 110L

/**
 * Bouton rond de télécommande.
 *
 * Appui : écrasement léger, fond éclairci, retour haptique. Les touches
 * répétables (volume) se répètent tant que le doigt reste posé.
 */
@Composable
fun RemoteButton(
    icon: ImageVector,
    description: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    repeatable: Boolean = false,
    secondaryIcon: ImageVector? = null,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val currentOnPress by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(stiffness = 900f), label = "scale")
    val background by animateColorAsState(
        if (pressed) RemoteColors.SurfacePressed else RemoteColors.SurfaceHigh,
        label = "background",
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(background)
            .border(1.dp, RemoteColors.Outline, CircleShape)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .pointerInput(repeatable) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        haptics.tap()
                        currentOnPress()
                        var repeatJob: Job? = null
                        if (repeatable) {
                            repeatJob = scope.launch {
                                delay(REPEAT_START_MS)
                                while (isActive) {
                                    haptics.tick()
                                    currentOnPress()
                                    delay(REPEAT_INTERVAL_MS)
                                }
                            }
                        }
                        tryAwaitRelease()
                        repeatJob?.cancel()
                        pressed = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (secondaryIcon == null) {
            Icon(icon, contentDescription = null, tint = RemoteColors.TextPrimary, modifier = Modifier.size(size * 0.42f))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = RemoteColors.TextPrimary, modifier = Modifier.size(size * 0.32f))
                Icon(secondaryIcon, contentDescription = null, tint = RemoteColors.TextPrimary, modifier = Modifier.size(size * 0.32f))
            }
        }
    }
}

/**
 * Pavé directionnel circulaire : quatre secteurs et un bouton OK central.
 *
 * Une seule zone tactile calcule la direction à partir de l'angle du doigt,
 * comme sur une vraie télécommande : pas de « trou » entre les flèches.
 * Les flèches maintenues se répètent (défilement rapide des listes).
 */
@Composable
fun DirectionalPad(
    onKey: (RemoteKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val currentOnKey by rememberUpdatedState(onKey)
    var pressedKey by remember { mutableStateOf<RemoteKey?>(null) }

    val highlight by animateFloatAsState(if (pressedKey != null) 1f else 0f, label = "highlight")
    val centerScale by animateFloatAsState(
        if (pressedKey == RemoteKey.CENTER) 0.92f else 1f,
        spring(stiffness = 900f),
        label = "center",
    )

    BoxWithConstraints(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        val padSize = maxWidth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(RemoteColors.SurfaceHigh, RemoteColors.Surface),
                    ),
                )
                .border(1.dp, RemoteColors.Outline, CircleShape)
                .semantics { contentDescription = "Pavé directionnel" }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { position ->
                            val key = keyAt(position, Size(size.width.toFloat(), size.height.toFloat()))
                                ?: return@detectTapGestures
                            pressedKey = key
                            haptics.tap()
                            currentOnKey(key)
                            val repeatJob = if (key.isRepeatable) {
                                scope.launch {
                                    delay(REPEAT_START_MS)
                                    while (isActive) {
                                        haptics.tick()
                                        currentOnKey(key)
                                        delay(REPEAT_INTERVAL_MS)
                                    }
                                }
                            } else {
                                null
                            }
                            tryAwaitRelease()
                            repeatJob?.cancel()
                            pressedKey = null
                        },
                    )
                },
        ) {
            // Secteur éclairé sous le doigt.
            Canvas(Modifier.fillMaxSize()) {
                val key = pressedKey ?: return@Canvas
                if (key == RemoteKey.CENTER) return@Canvas
                val start = when (key) {
                    RemoteKey.RIGHT -> -45f
                    RemoteKey.DOWN -> 45f
                    RemoteKey.LEFT -> 135f
                    else -> 225f
                }
                drawArc(
                    brush = Brush.radialGradient(
                        colors = listOf(RemoteColors.Accent.copy(alpha = 0.05f), RemoteColors.Accent.copy(alpha = 0.32f * highlight)),
                        center = center,
                        radius = size.minDimension / 2f,
                    ),
                    startAngle = start,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = Offset.Zero,
                    size = size,
                )
            }

            val arrowSize = padSize * 0.13f
            val arrowPadding = padSize * 0.06f
            PadArrow(Icons.Rounded.KeyboardArrowUp, pressedKey == RemoteKey.UP, arrowSize, Modifier.align(Alignment.TopCenter).padding(top = arrowPadding))
            PadArrow(Icons.Rounded.KeyboardArrowDown, pressedKey == RemoteKey.DOWN, arrowSize, Modifier.align(Alignment.BottomCenter).padding(bottom = arrowPadding))
            PadArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, pressedKey == RemoteKey.LEFT, arrowSize, Modifier.align(Alignment.CenterStart).padding(start = arrowPadding))
            PadArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, pressedKey == RemoteKey.RIGHT, arrowSize, Modifier.align(Alignment.CenterEnd).padding(end = arrowPadding))

            // Bouton OK central.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(padSize * CENTER_RATIO * 2f)
                    .graphicsLayer {
                        scaleX = centerScale
                        scaleY = centerScale
                    }
                    .clip(CircleShape)
                    .background(
                        if (pressedKey == RemoteKey.CENTER) {
                            Brush.linearGradient(listOf(RemoteColors.AccentSoft, RemoteColors.Accent))
                        } else {
                            Brush.linearGradient(listOf(RemoteColors.SurfacePressed, RemoteColors.SurfaceHigh))
                        },
                    )
                    .border(1.dp, RemoteColors.Outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("OK", style = MaterialTheme.typography.titleMedium, color = RemoteColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun PadArrow(icon: ImageVector, pressed: Boolean, size: Dp, modifier: Modifier) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (pressed) RemoteColors.TextPrimary else RemoteColors.TextSecondary,
        modifier = modifier.size(size),
    )
}

/** Proportion du rayon occupée par le bouton OK. */
private const val CENTER_RATIO = 0.19f

/** Touche correspondant à un point du pavé ; `null` hors du disque. */
private fun keyAt(position: Offset, size: Size): RemoteKey? {
    val radius = size.minDimension / 2f
    val dx = position.x - size.width / 2f
    val dy = position.y - size.height / 2f
    val distance = hypot(dx, dy)
    return when {
        distance > radius -> null
        distance < radius * CENTER_RATIO * 2f -> RemoteKey.CENTER
        abs(dx) > abs(dy) -> if (dx > 0) RemoteKey.RIGHT else RemoteKey.LEFT
        else -> if (dy > 0) RemoteKey.DOWN else RemoteKey.UP
    }
}

/** Halo d'ambiance en haut de l'écran, teinté par l'accent. */
@Composable
fun AmbientGlow(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().aspectRatio(1f)) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(RemoteColors.Accent.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(size.width * 0.3f, 0f),
                radius = size.width * 0.9f,
            ),
            radius = size.width * 0.9f,
            center = Offset(size.width * 0.3f, 0f),
        )
    }
}
