package com.homehub.remote.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homehub.remote.ui.theme.RemoteColors
import com.homehub.remote.viewmodel.Banner
import com.homehub.remote.voice.VoiceState

/**
 * Bouton micro : la pièce maîtresse de la télécommande.
 *
 * Au repos, une pastille bleue lumineuse. Pendant l'écoute, des ondes
 * concentriques s'élargissent au rythme de la voix (niveau sonore réel du
 * micro), et l'icône devient « Stop ».
 */
@Composable
fun MicButton(
    state: VoiceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
) {
    val haptics = rememberHaptics()
    val listening = state is VoiceState.Listening || state is VoiceState.Processing
    val level = (state as? VoiceState.Listening)?.level ?: 0f

    val smoothLevel by animateFloatAsState(level, spring(stiffness = 300f), label = "level")
    val transition = rememberInfiniteTransition(label = "waves")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Restart),
        label = "wave",
    )
    val activeScale by animateFloatAsState(if (listening) 1.06f else 1f, spring(stiffness = 500f), label = "scale")

    Box(
        modifier = modifier
            .size(size * 1.7f)
            .drawBehind {
                val baseRadius = this.size.minDimension / 2f / 1.7f
                if (listening) {
                    // Deux ondes décalées + un halo qui suit la voix.
                    for (offset in listOf(0f, 0.5f)) {
                        val progress = (wave + offset) % 1f
                        drawCircle(
                            color = RemoteColors.Accent.copy(alpha = 0.35f * (1f - progress)),
                            radius = baseRadius * (1f + 0.7f * progress),
                        )
                    }
                    drawCircle(
                        color = RemoteColors.Accent.copy(alpha = 0.22f),
                        radius = baseRadius * (1.05f + 0.45f * smoothLevel),
                    )
                } else {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(RemoteColors.Accent.copy(alpha = 0.28f), RemoteColors.Accent.copy(alpha = 0f)),
                            radius = baseRadius * 1.6f,
                        ),
                        radius = baseRadius * 1.6f,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = activeScale
                    scaleY = activeScale
                }
                .background(
                    Brush.linearGradient(listOf(RemoteColors.AccentSoft, RemoteColors.Accent)),
                    CircleShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    haptics.confirm()
                    onClick()
                }
                .semantics { contentDescription = if (listening) "Arrêter l'écoute" else "Parler à la TV" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = null,
                tint = RemoteColors.TextPrimary,
                modifier = Modifier.size(size * 0.42f),
            )
        }
    }
}

/**
 * Carte de transcription : « Je vous écoute… » puis les mots au fur et à
 * mesure qu'ils sont reconnus. Affiche aussi les erreurs du micro.
 */
@Composable
fun VoicePanel(state: VoiceState, onDismissError: () -> Unit, modifier: Modifier = Modifier) {
    val visible = state !is VoiceState.Idle
    // Pendant l'animation de sortie, on garde le dernier contenu affiché.
    var last by remember { mutableStateOf<VoiceState>(VoiceState.Idle) }
    SideEffect { if (state !is VoiceState.Idle) last = state }
    val shown = if (state is VoiceState.Idle) last else state
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 3 },
        exit = fadeOut() + slideOutVertically { it / 3 },
        modifier = modifier,
    ) {
        val (title, text, isError) = when (shown) {
            is VoiceState.Listening -> Triple("Je vous écoute…", shown.partial, false)
            is VoiceState.Processing -> Triple("Je réfléchis…", shown.partial, false)
            is VoiceState.Error -> Triple("Micro", shown.message, true)
            VoiceState.Idle -> Triple("", "", false)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RemoteColors.SurfaceHigh.copy(alpha = 0.97f), RoundedCornerShape(24.dp))
                .border(1.dp, if (isError) RemoteColors.Danger.copy(alpha = 0.5f) else RemoteColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .then(if (isError) Modifier.clickable(onClick = onDismissError) else Modifier)
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = if (isError) RemoteColors.Danger else RemoteColors.AccentSoft,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = text.ifBlank { "Dites par exemple « Ouvre Netflix » ou « Cherche Dune sur YouTube »" },
                style = if (text.isBlank()) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.headlineSmall,
                color = if (text.isBlank()) RemoteColors.TextTertiary else RemoteColors.TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Bandeau de résultat renvoyé par la TV (« Ouverture de Netflix »). */
@Composable
fun ResultBanner(banner: Banner?, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf<Banner?>(null) }
    SideEffect { if (banner != null) last = banner }
    AnimatedVisibility(
        visible = banner != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val current = banner ?: last ?: return@AnimatedVisibility
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RemoteColors.SurfaceHigh, RoundedCornerShape(18.dp))
                .border(1.dp, RemoteColors.Outline, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(
                imageVector = if (current.ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = if (current.ok) RemoteColors.Success else RemoteColors.Danger,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                current.query?.let { query ->
                    Text(
                        text = "« $query »",
                        style = MaterialTheme.typography.titleSmall,
                        color = RemoteColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RemoteColors.TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
