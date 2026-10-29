package com.homehub.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.homehub.launcher.remote.RemoteFeedback
import com.homehub.launcher.ui.theme.HubColors
import com.homehub.launcher.ui.theme.LocalAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * Bandeau qui confirme, en bas de l'écran, une commande reçue du téléphone :
 * la phrase entendue, puis l'action effectuée. Disparaît seul après 3,5 s.
 */
@Composable
fun RemoteFeedbackBanner(events: Flow<RemoteFeedback>, modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<RemoteFeedback?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(events) {
        events.collect { feedback ->
            current = feedback
            visible = true
        }
    }
    LaunchedEffect(current) {
        if (current != null) {
            delay(3_500)
            visible = false
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.padding(bottom = 36.dp),
        ) {
            current?.let { BannerContent(it) }
        }
    }
}

@Composable
private fun BannerContent(feedback: RemoteFeedback) {
    val accent = LocalAccent.current.value
    Row(
        modifier = Modifier
            .widthIn(max = 620.dp)
            .background(HubColors.CardRaised.copy(alpha = 0.96f), RoundedCornerShape(28.dp))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(if (feedback.ok) accent else HubColors.TextTertiary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            MicGlyph(Color.White, Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            feedback.query?.let { query ->
                Text(
                    text = "« $query »",
                    style = MaterialTheme.typography.titleMedium,
                    color = HubColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = feedback.message,
                style = MaterialTheme.typography.bodyMedium,
                color = HubColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Micro : capsule, arceau et pied. */
@Composable
fun MicGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.11f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.34f, h * 0.04f),
            size = Size(w * 0.32f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.16f),
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.18f, h * 0.18f),
            size = Size(w * 0.64f, h * 0.6f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(color, Offset(w * 0.5f, h * 0.78f), Offset(w * 0.5f, h * 0.95f), stroke, StrokeCap.Round)
    }
}
