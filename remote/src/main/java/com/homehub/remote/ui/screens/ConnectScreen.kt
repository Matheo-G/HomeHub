package com.homehub.remote.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homehub.remote.data.BoxAddress
import com.homehub.remote.data.ConnectionState
import com.homehub.remote.ui.theme.RemoteColors
import com.homehub.remote.viewmodel.RemoteViewModel

/**
 * Choix de la TV : box trouvées automatiquement sur le Wi-Fi, ou adresse
 * saisie à la main en secours.
 */
@Composable
fun ConnectScreen(viewModel: RemoteViewModel, modifier: Modifier = Modifier) {
    val boxes by viewModel.discoveredBoxes.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    var manualOpen by rememberSaveable { mutableStateOf(false) }
    var manualAddress by rememberSaveable { mutableStateOf("") }
    var manualError by rememberSaveable { mutableStateOf(false) }

    val connectingTo = (connection as? ConnectionState.Connecting)?.box
    val failure = connection as? ConnectionState.Failed

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            Column {
                Text("HomeHub Remote", style = MaterialTheme.typography.labelMedium, color = RemoteColors.AccentSoft)
                Spacer(Modifier.height(8.dp))
                Text("Choisissez votre TV", style = MaterialTheme.typography.displaySmall, color = RemoteColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Le téléphone et la TV doivent être connectés au même Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RemoteColors.TextTertiary,
                )
                Spacer(Modifier.height(14.dp))
            }
        }

        if (failure != null) {
            item(key = "failure") {
                NoticeCard(
                    title = "Connexion impossible",
                    text = failure.message,
                    actionLabel = "Réessayer",
                    onAction = { viewModel.connect(failure.box) },
                )
            }
        }

        item(key = "listHeader") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "TV À PROXIMITÉ",
                    style = MaterialTheme.typography.labelMedium,
                    color = RemoteColors.TextTertiary,
                    modifier = Modifier.weight(1f),
                )
                if (searching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = RemoteColors.AccentSoft,
                    )
                }
                IconButton(onClick = viewModel::refreshDiscovery) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Actualiser", tint = RemoteColors.TextSecondary)
                }
            }
        }

        if (boxes.isEmpty()) {
            item(key = "empty") {
                NoticeCard(
                    title = if (searching) "Recherche en cours…" else "Aucune TV trouvée",
                    text = "Sur la TV, ouvrez HomeHub → Paramètres → Télécommande mobile et vérifiez " +
                        "qu'elle est activée. Vous pouvez aussi saisir son adresse ci-dessous.",
                )
            }
        } else {
            items(boxes, key = { it.key }) { box ->
                BoxCard(
                    box = box,
                    connecting = connectingTo?.key == box.key,
                    onClick = { viewModel.connect(box) },
                )
            }
        }

        item(key = "manual") {
            Column(modifier = Modifier.animateContentSize()) {
                TextButton(onClick = { manualOpen = !manualOpen }) {
                    Text(
                        if (manualOpen) "Masquer la saisie manuelle" else "Saisir l'adresse de la TV",
                        color = RemoteColors.AccentSoft,
                    )
                }
                AnimatedVisibility(visible = manualOpen) {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = manualAddress,
                            onValueChange = {
                                manualAddress = it
                                manualError = false
                            },
                            label = { Text("Adresse (ex. 192.168.1.20)") },
                            supportingText = {
                                Text(
                                    if (manualError) {
                                        "Adresse invalide"
                                    } else {
                                        "Affichée sur la TV : Paramètres → Télécommande activée"
                                    },
                                )
                            },
                            isError = manualError,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { manualError = !viewModel.connectManual(manualAddress) }),
                            trailingIcon = {
                                IconButton(onClick = { manualError = !viewModel.connectManual(manualAddress) }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Se connecter")
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RemoteColors.Accent,
                                unfocusedBorderColor = RemoteColors.Outline,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxCard(box: BoxAddress, connecting: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RemoteColors.Surface, RoundedCornerShape(20.dp))
            .border(1.dp, if (connecting) RemoteColors.Accent else RemoteColors.Outline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(RemoteColors.Accent.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Tv, contentDescription = null, tint = RemoteColors.AccentSoft)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(box.name, style = MaterialTheme.typography.titleMedium, color = RemoteColors.TextPrimary)
            Text(box.host, style = MaterialTheme.typography.bodySmall, color = RemoteColors.TextTertiary)
        }
        if (connecting) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = RemoteColors.Accent)
        } else {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = RemoteColors.TextTertiary)
        }
    }
}

/** Carte d'information ou d'erreur, avec action facultative. */
@Composable
internal fun NoticeCard(
    title: String,
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RemoteColors.Surface, RoundedCornerShape(20.dp))
            .border(1.dp, RemoteColors.Outline, RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (actionLabel != null) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = RemoteColors.Danger, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = RemoteColors.TextPrimary)
        }
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = RemoteColors.TextSecondary)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                Text(actionLabel, color = RemoteColors.AccentSoft)
            }
        }
    }
}
