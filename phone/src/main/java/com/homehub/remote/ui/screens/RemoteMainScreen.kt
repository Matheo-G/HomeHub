package com.homehub.remote.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homehub.protocol.AppEntry
import com.homehub.protocol.RemoteKey
import com.homehub.remote.data.ConnectionState
import com.homehub.remote.ui.components.DirectionalPad
import com.homehub.remote.ui.components.MicButton
import com.homehub.remote.ui.components.RemoteButton
import com.homehub.remote.ui.components.ResultBanner
import com.homehub.remote.ui.components.VoicePanel
import com.homehub.remote.ui.theme.RemoteColors
import com.homehub.remote.viewmodel.RemoteViewModel
import com.homehub.remote.voice.VoiceState

/** Onglets de la télécommande. */
private enum class RemoteTab(val label: String, val icon: ImageVector) {
    Remote("Télécommande", Icons.Rounded.SettingsRemote),
    Apps("Applis", Icons.Rounded.Apps),
    Keyboard("Clavier", Icons.Rounded.Keyboard),
}

/**
 * Écran principal : en-tête (TV connectée), onglets, et une barre basse
 * toujours visible avec Retour, le micro et Accueil.
 *
 * @param onMicClick action du micro (gère permission et repli système).
 */
@Composable
fun RemoteMainScreen(
    viewModel: RemoteViewModel,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(RemoteTab.Remote) }
    var showSources by rememberSaveable { mutableStateOf(false) }

    if (showSources) {
        SourcesScreen(viewModel = viewModel, onBack = { showSources = false }, modifier = modifier)
        return
    }

    val connected = connection as? ConnectionState.Connected
    val reconnecting = connection is ConnectionState.Connecting
    val boxName = when (val state = connection) {
        is ConnectionState.Connected -> state.info.name
        is ConnectionState.Connecting -> state.box.name
        else -> ""
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Header(
                boxName = boxName,
                reconnecting = reconnecting,
                onChangeBox = viewModel::changeBox,
                onOpenSources = { showSources = true },
            )
            ResultBanner(banner, Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(10.dp))
            TabSelector(selected = tab, onSelect = { tab = it }, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "tabs",
            ) { current ->
                when (current) {
                    RemoteTab.Remote -> RemoteTabContent(
                        fullControl = connected?.fullControl ?: true,
                        onKey = viewModel::press,
                    )
                    RemoteTab.Apps -> AppsTabContent(viewModel)
                    RemoteTab.Keyboard -> KeyboardTabContent(
                        fullControl = connected?.fullControl ?: true,
                        onText = viewModel::typeText,
                        onKey = viewModel::press,
                    )
                }
            }

            BottomBar(
                voice = voice,
                onMicClick = onMicClick,
                onKey = viewModel::press,
            )
        }

        // La transcription s'affiche au-dessus de la barre basse.
        VoicePanel(
            state = voice,
            onDismissError = viewModel::clearVoiceError,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = 150.dp),
        )
    }
}

@Composable
private fun Header(boxName: String, reconnecting: Boolean, onChangeBox: () -> Unit, onOpenSources: () -> Unit) {
    val dotColor by animateColorAsState(if (reconnecting) RemoteColors.Danger else RemoteColors.Success, label = "dot")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(dotColor, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (reconnecting) "RECONNEXION…" else "CONNECTÉ",
                    style = MaterialTheme.typography.labelMedium,
                    color = RemoteColors.TextTertiary,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = boxName,
                style = MaterialTheme.typography.headlineSmall,
                color = RemoteColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenSources) {
            Icon(Icons.AutoMirrored.Rounded.ManageSearch, contentDescription = "Sources de recherche", tint = RemoteColors.TextSecondary)
        }
        IconButton(onClick = onChangeBox) {
            Icon(Icons.Rounded.SwapHoriz, contentDescription = "Changer de TV", tint = RemoteColors.TextSecondary)
        }
    }
}

/** Sélecteur d'onglets en pilule, avec indicateur glissant. */
@Composable
private fun TabSelector(selected: RemoteTab, onSelect: (RemoteTab) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(RemoteColors.Surface, RoundedCornerShape(23.dp))
            .border(1.dp, RemoteColors.Outline, RoundedCornerShape(23.dp))
            .padding(4.dp),
    ) {
        val tabWidth = maxWidth / RemoteTab.entries.size
        val indicatorOffset by animateDpAsState(tabWidth * selected.ordinal, label = "indicator")
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .fillMaxSize()
                .background(RemoteColors.SurfacePressed, RoundedCornerShape(19.dp)),
        )
        Row(Modifier.fillMaxSize()) {
            RemoteTab.entries.forEach { tab ->
                val active = tab == selected
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(19.dp))
                        .clickable { onSelect(tab) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = null,
                        tint = if (active) RemoteColors.TextPrimary else RemoteColors.TextTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) RemoteColors.TextPrimary else RemoteColors.TextTertiary,
                    )
                }
            }
        }
    }
}

/** Onglet Télécommande : pavé directionnel, lecture et volume. */
@Composable
private fun RemoteTabContent(fullControl: Boolean, onKey: (RemoteKey) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!fullControl) {
            Text(
                text = "Navigation limitée à HomeHub : activez « Contrôle complet » dans les Paramètres HomeHub " +
                    "de la TV pour piloter aussi Netflix, YouTube…",
                style = MaterialTheme.typography.bodySmall,
                color = RemoteColors.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        DirectionalPad(
            onKey = onKey,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth(0.82f)
                .padding(vertical = 8.dp),
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteButton(Icons.Rounded.SkipPrevious, "Précédent", { onKey(RemoteKey.PREVIOUS) }, size = 52.dp)
            RemoteButton(Icons.Rounded.FastRewind, "Retour rapide", { onKey(RemoteKey.REWIND) }, size = 52.dp)
            RemoteButton(
                Icons.Rounded.PlayArrow,
                "Lecture / pause",
                { onKey(RemoteKey.PLAY_PAUSE) },
                size = 52.dp,
                secondaryIcon = Icons.Rounded.Pause,
            )
            RemoteButton(Icons.Rounded.FastForward, "Avance rapide", { onKey(RemoteKey.FAST_FORWARD) }, size = 52.dp)
            RemoteButton(Icons.Rounded.SkipNext, "Suivant", { onKey(RemoteKey.NEXT) }, size = 52.dp)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            RemoteButton(Icons.AutoMirrored.Rounded.VolumeDown, "Baisser le volume", { onKey(RemoteKey.VOLUME_DOWN) }, repeatable = true)
            RemoteButton(Icons.AutoMirrored.Rounded.VolumeOff, "Couper le son", { onKey(RemoteKey.MUTE) })
            RemoteButton(Icons.AutoMirrored.Rounded.VolumeUp, "Monter le volume", { onKey(RemoteKey.VOLUME_UP) }, repeatable = true)
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Onglet Applis : favoris puis toutes les applications de la TV. */
@Composable
private fun AppsTabContent(viewModel: RemoteViewModel) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val icons by viewModel.icons.collectAsStateWithLifecycle()
    val favorites = apps.filter { it.favorite }

    if (apps.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Chargement des applications de la TV…", color = RemoteColors.TextTertiary)
            TextButton(onClick = viewModel::refreshApps) { Text("Actualiser", color = RemoteColors.AccentSoft) }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (favorites.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "favHeader") { SectionLabel("Favoris") }
            items(favorites, key = { "fav:" + it.packageName }) { app ->
                AppTile(app, icons[app.packageName]) { viewModel.launch(app) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "allHeader") { SectionLabel("Toutes les applications") }
        items(apps, key = { it.packageName }) { app ->
            AppTile(app, icons[app.packageName]) { viewModel.launch(app) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = RemoteColors.TextTertiary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun AppTile(app: AppEntry, icon: ImageBitmap?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(RemoteColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                )
            } else {
                Text(app.label.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = RemoteColors.TextTertiary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodySmall,
            color = RemoteColors.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Onglet Clavier : ce qui est tapé apparaît en direct dans le champ de
 * recherche actif de la TV (Netflix, YouTube…).
 */
@Composable
private fun KeyboardTabContent(
    fullControl: Boolean,
    onText: (String) -> Unit,
    onKey: (RemoteKey) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Text(
            text = if (fullControl) {
                "Placez-vous sur un champ de recherche de la TV, puis tapez ici : le texte s'affiche en direct."
            } else {
                "La saisie nécessite le « Contrôle complet » (TV : Paramètres HomeHub → Contrôle complet)."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = RemoteColors.TextTertiary,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onText(it)
            },
            placeholder = { Text("Rechercher sur la TV…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onKey(RemoteKey.CENTER) }),
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = {
                        text = ""
                        onText("")
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Effacer")
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RemoteColors.Accent,
                unfocusedBorderColor = RemoteColors.Outline,
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteButton(Icons.Rounded.PlayArrow, "Valider (OK)", { onKey(RemoteKey.CENTER) }, size = 54.dp)
            Text("Valider sur la TV (OK)", style = MaterialTheme.typography.bodyMedium, color = RemoteColors.TextSecondary)
        }
    }
}

/** Barre basse : Retour, micro, Accueil — accessibles depuis tous les onglets. */
@Composable
private fun BottomBar(
    voice: VoiceState,
    onMicClick: () -> Unit,
    onKey: (RemoteKey) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteButton(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", { onKey(RemoteKey.BACK) }, size = 62.dp)
        MicButton(state = voice, onClick = onMicClick)
        RemoteButton(Icons.Rounded.Home, "Accueil", { onKey(RemoteKey.HOME) }, size = 62.dp)
    }
}
