package com.homehub.remote.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homehub.protocol.Protocol
import com.homehub.remote.data.ConnectionState
import com.homehub.remote.ui.components.rememberHaptics
import com.homehub.remote.ui.theme.RemoteColors

/**
 * Appairage : saisie du code à 4 chiffres affiché sur la TV.
 *
 * Tant que l'écran d'appairage n'est pas ouvert sur la TV, l'application
 * indique où le trouver et bascule seule sur la saisie dès qu'il s'ouvre.
 * Le code est envoyé automatiquement au 4ᵉ chiffre ; une erreur secoue les cases.
 */
@Composable
fun PairScreen(
    state: ConnectionState.NeedsPairing,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val shake = remember { Animatable(0f) }
    val haptics = rememberHaptics()

    LaunchedEffect(state.pairingOpen) {
        if (state.pairingOpen) runCatching { focusRequester.requestFocus() }
    }
    // Code refusé : on vide les cases et on secoue.
    LaunchedEffect(state.error) {
        if (state.error != null && state.pairingOpen) {
            pin = ""
            haptics.confirm()
            for (offset in listOf(18f, -14f, 10f, -6f, 0f)) {
                shake.animateTo(offset, spring(stiffness = 2_000f))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Text("ASSOCIATION", style = MaterialTheme.typography.labelMedium, color = RemoteColors.AccentSoft)
        Spacer(Modifier.height(8.dp))
        Text(state.info.name, style = MaterialTheme.typography.displaySmall, color = RemoteColors.TextPrimary)
        Spacer(Modifier.height(10.dp))

        if (!state.pairingOpen) {
            Text(
                "Sur la TV, ouvrez HomeHub → Paramètres → Télécommande mobile → « Associer un téléphone ».",
                style = MaterialTheme.typography.bodyLarge,
                color = RemoteColors.TextSecondary,
            )
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = RemoteColors.AccentSoft)
                Spacer(Modifier.width(12.dp))
                Text("En attente de la TV…", style = MaterialTheme.typography.bodyMedium, color = RemoteColors.TextTertiary)
            }
        } else {
            Text(
                "Saisissez le code affiché sur la TV.",
                style = MaterialTheme.typography.bodyLarge,
                color = RemoteColors.TextSecondary,
            )
            Spacer(Modifier.height(32.dp))

            BasicTextField(
                value = pin,
                onValueChange = { value ->
                    val digits = value.filter(Char::isDigit).take(Protocol.PIN_LENGTH)
                    pin = digits
                    if (digits.length == Protocol.PIN_LENGTH) onSubmit(digits)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                enabled = !state.submitting,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .graphicsLayer { translationX = shake.value },
                decorationBox = { innerTextField ->
                    // Le champ réel reste composé (saisie, curseur) mais invisible : seules les cases s'affichent.
                    Box(Modifier.size(1.dp).alpha(0f)) { innerTextField() }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(Protocol.PIN_LENGTH) { index ->
                            PinCell(
                                digit = pin.getOrNull(index),
                                active = index == pin.length && !state.submitting,
                                error = state.error != null && pin.isEmpty(),
                            )
                        }
                    }
                },
            )

            Spacer(Modifier.height(18.dp))
            when {
                state.submitting -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = RemoteColors.AccentSoft)
                    Spacer(Modifier.width(10.dp))
                    Text("Vérification…", style = MaterialTheme.typography.bodyMedium, color = RemoteColors.TextTertiary)
                }
                state.error != null -> Text(state.error, style = MaterialTheme.typography.bodyMedium, color = RemoteColors.Danger)
            }
        }

        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Choisir une autre TV", color = RemoteColors.TextSecondary)
        }
    }
}

@Composable
private fun PinCell(digit: Char?, active: Boolean, error: Boolean) {
    val borderColor = when {
        error -> RemoteColors.Danger
        active -> RemoteColors.Accent
        digit != null -> RemoteColors.AccentSoft.copy(alpha = 0.5f)
        else -> RemoteColors.Outline
    }
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 78.dp)
            .background(RemoteColors.Surface, RoundedCornerShape(18.dp))
            .border(if (active) 2.dp else 1.dp, borderColor, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = digit?.toString() ?: "",
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            color = RemoteColors.TextPrimary,
        )
    }
}
