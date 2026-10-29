package com.homehub.launcher.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.homehub.launcher.data.models.ConnectionStatus
import com.homehub.launcher.ui.theme.HubColors
import com.homehub.launcher.utils.Clock
import kotlinx.coroutines.flow.StateFlow

/**
 * Barre d'état très discrète : salutation et date à gauche, réseau et heure à droite.
 *
 * Elle collecte elle-même l'horloge et l'état réseau : ce sont les seuls
 * éléments de l'accueil qui changent avec le temps, et leur recomposition
 * reste confinée à cette barre.
 */
@Composable
fun StatusBar(
    userName: String,
    connectionStatus: StateFlow<ConnectionStatus>,
    modifier: Modifier = Modifier,
) {
    val ticks = remember { Clock.minuteTicks() }
    val now by ticks.collectAsStateWithLifecycle(initialValue = System.currentTimeMillis())
    val connection by connectionStatus.collectAsStateWithLifecycle()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${Clock.greeting(now)}, $userName",
                style = MaterialTheme.typography.headlineSmall,
                color = HubColors.TextPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = Clock.formatDate(now),
                style = MaterialTheme.typography.bodyMedium,
                color = HubColors.TextTertiary,
                maxLines = 1,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            ConnectionIndicator(connection)
            Spacer(Modifier.width(18.dp))
            Text(
                text = Clock.formatTime(now),
                style = MaterialTheme.typography.displaySmall,
                color = HubColors.TextPrimary,
                maxLines = 1,
            )
        }
    }
}

/** Pictogramme réseau, avec un fondu enchaîné à chaque changement. */
@Composable
private fun ConnectionIndicator(status: ConnectionStatus) {
    AnimatedContent(
        targetState = status,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "connection",
    ) { current ->
        val glyphModifier = Modifier.size(20.dp)
        when (current) {
            is ConnectionStatus.Wifi -> WifiGlyph(current.level, HubColors.TextSecondary, glyphModifier)
            ConnectionStatus.Ethernet -> EthernetGlyph(HubColors.TextSecondary, glyphModifier)
            ConnectionStatus.Offline -> OfflineGlyph(HubColors.TextTertiary, glyphModifier)
        }
    }
}
