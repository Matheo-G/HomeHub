package com.homehub.launcher.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.homehub.launcher.BuildConfig
import com.homehub.launcher.data.models.LauncherSettings
import com.homehub.launcher.remote.PairedPhone
import com.homehub.launcher.ui.components.CheckGlyph
import com.homehub.launcher.ui.components.FocusableSurface
import com.homehub.launcher.ui.components.QrCode
import com.homehub.launcher.ui.components.SectionTitle
import com.homehub.launcher.ui.components.awaitLayout
import com.homehub.launcher.ui.theme.HubColors
import com.homehub.launcher.ui.theme.HubDimens
import com.homehub.launcher.ui.theme.LocalAccent
import com.homehub.launcher.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

/**
 * Section « Télécommande mobile » des Paramètres : appairage, contrôle
 * complet, activation et révocation des téléphones.
 */
@Composable
internal fun RemoteSection(
    viewModel: SettingsViewModel,
    settings: LauncherSettings,
    pairCardFocus: FocusRequester,
    onOpenPairing: () -> Unit,
) {
    val status by viewModel.remoteStatus.collectAsStateWithLifecycle()
    val fullControl by viewModel.fullControlEnabled.collectAsStateWithLifecycle()
    val phones by viewModel.pairedPhones.collectAsStateWithLifecycle()
    var confirmForget by remember { mutableStateOf(false) }

    // La confirmation de révocation expire d'elle-même.
    LaunchedEffect(confirmForget) {
        if (confirmForget) {
            delay(4_000)
            confirmForget = false
        }
    }

    Column {
        SectionTitle("Télécommande mobile")
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            ActionCard(
                title = "Associer un téléphone",
                subtitle = phonesSummary(phones, status.connectedPhones),
                onClick = onOpenPairing,
                focusRequester = pairCardFocus,
            )
            ActionCard(
                title = "Contrôle complet",
                subtitle = if (fullControl) {
                    "Activé : le téléphone pilote aussi Netflix, YouTube…"
                } else {
                    "À activer : Accessibilité → HomeHub, pour piloter toutes les applis"
                },
                onClick = viewModel::openAccessibilitySettings,
                highlighted = fullControl,
            )
        }
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            ActionCard(
                title = if (settings.remoteEnabled) "Télécommande activée" else "Télécommande désactivée",
                subtitle = when {
                    !settings.remoteEnabled -> "Appuyez pour autoriser les téléphones à se connecter"
                    status.running && status.address != null -> "Joignable sur ${status.address}:${status.port}"
                    else -> "Démarrage…"
                },
                onClick = { viewModel.setRemoteEnabled(!settings.remoteEnabled) },
                highlighted = settings.remoteEnabled,
            )
            if (phones.isNotEmpty()) {
                ActionCard(
                    title = if (confirmForget) "Confirmer ?" else "Oublier les téléphones",
                    subtitle = if (confirmForget) {
                        "Appuyez de nouveau : ils devront être réassociés"
                    } else {
                        phones.joinToString(", ") { it.name }
                    },
                    onClick = {
                        if (confirmForget) {
                            viewModel.forgetPhones()
                            confirmForget = false
                        } else {
                            confirmForget = true
                        }
                    },
                )
            }
        }
    }
}

private fun phonesSummary(phones: List<PairedPhone>, connected: Int): String = when {
    phones.isEmpty() -> "Pilotez la TV et parlez-lui depuis votre téléphone"
    connected > 0 -> "${phones.size} associé(s) · $connected connecté(s)"
    else -> "${phones.size} téléphone(s) associé(s)"
}

/**
 * Panneau d'appairage plein écran :
 *  1. QR code pour télécharger HomeHub Remote ;
 *  2. nom de la box à choisir dans l'application ;
 *  3. code à saisir.
 * Une confirmation animée s'affiche quand un téléphone s'appaire.
 */
@Composable
internal fun PairingPanel(
    viewModel: SettingsViewModel,
    pin: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status by viewModel.remoteStatus.collectAsStateWithLifecycle()
    var paired by remember { mutableStateOf<PairedPhone?>(null) }
    val closeFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.newlyPaired.collect { phone -> paired = phone }
    }
    LaunchedEffect(paired) {
        if (paired != null) {
            delay(2_600)
            onClose()
        }
    }
    LaunchedEffect(Unit) {
        awaitLayout()
        runCatching { closeFocus.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(780.dp)
                .background(HubColors.Card.copy(alpha = 0.96f), RoundedCornerShape(28.dp))
                .border(1.dp, HubColors.CardOutline, RoundedCornerShape(28.dp))
                .padding(horizontal = 40.dp, vertical = 32.dp),
        ) {
            AnimatedContent(
                targetState = paired,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut() },
                label = "pairing",
            ) { phone ->
                if (phone == null) {
                    PairingInstructions(
                        pin = pin,
                        boxName = status.boxName.ifBlank { "votre box" },
                        address = status.address?.let { "$it:${status.port}" },
                        running = status.running,
                    )
                } else {
                    PairingSuccess(phone.name)
                }
            }
            Spacer(Modifier.height(24.dp))
            FocusableSurface(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.End)
                    .size(width = 150.dp, height = 48.dp),
                cornerRadius = 24.dp,
                focusedScale = 1.06f,
                focusRequester = closeFocus,
            ) {
                Text(
                    text = if (paired == null) "Fermer" else "Terminé",
                    style = MaterialTheme.typography.titleMedium,
                    color = HubColors.TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun PairingInstructions(pin: String?, boxName: String, address: String?, running: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            QrCode(content = BuildConfig.REMOTE_APK_URL, modifier = Modifier.size(196.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Scannez pour installer\nHomeHub Remote",
                style = MaterialTheme.typography.bodySmall,
                color = HubColors.TextTertiary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.width(44.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Associer un téléphone",
                style = MaterialTheme.typography.headlineMedium,
                color = HubColors.TextPrimary,
            )
            Spacer(Modifier.height(18.dp))
            Step(number = 1, text = "Installez HomeHub Remote (QR code) sur votre téléphone Android.")
            Step(number = 2, text = "Ouvrez l'application et choisissez « $boxName ».")
            Step(number = 3, text = "Saisissez ce code :")
            Spacer(Modifier.height(14.dp))
            if (running && pin != null) {
                PinDigits(pin)
            } else {
                Text(
                    text = "Démarrage de la télécommande…",
                    style = MaterialTheme.typography.titleMedium,
                    color = HubColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Téléphone et TV doivent être sur le même Wi-Fi" + (address?.let { " · Box : $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = HubColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        val accent = LocalAccent.current.value
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(accent.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "$number", style = MaterialTheme.typography.labelMedium, color = HubColors.TextPrimary)
        }
        Spacer(Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = HubColors.TextSecondary)
    }
}

/** Code d'appairage : un chiffre par case, très lisible depuis le canapé. */
@Composable
private fun PinDigits(pin: String) {
    val accent = LocalAccent.current.value
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        pin.forEach { digit ->
            Box(
                modifier = Modifier
                    .size(width = 62.dp, height = 76.dp)
                    .background(HubColors.CardRaised, RoundedCornerShape(16.dp))
                    .border(1.5.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = digit.toString(),
                    color = HubColors.TextPrimary,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PairingSuccess(phoneName: String) {
    val accent = LocalAccent.current.value
    Column(
        modifier = Modifier.padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            CheckGlyph(Color.White, Modifier.size(44.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = "« $phoneName » est associé",
            style = MaterialTheme.typography.headlineMedium,
            color = HubColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(HubDimens.FavoriteWidth * 3),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Votre téléphone est maintenant une télécommande, micro compris.",
            style = MaterialTheme.typography.bodyMedium,
            color = HubColors.TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}
