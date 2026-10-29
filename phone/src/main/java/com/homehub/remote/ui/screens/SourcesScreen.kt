package com.homehub.remote.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homehub.remote.ui.theme.RemoteColors
import com.homehub.remote.viewmodel.RemoteViewModel
import kotlinx.coroutines.launch

/**
 * Sources de la recherche universelle : configuration de la clé TMDB.
 *
 * Sans clé, « Recherche Interstellar » propose de chercher dans les
 * applications installées ; avec une clé, la TV affiche films, séries,
 * personnes et leur disponibilité par service.
 */
@Composable
fun SourcesScreen(viewModel: RemoteViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val configured by viewModel.tmdbConfigured.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour", tint = RemoteColors.TextSecondary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Sources de recherche", style = MaterialTheme.typography.headlineSmall, color = RemoteColors.TextPrimary)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "« Recherche Interstellar » interroge ces sources depuis votre téléphone, puis affiche les résultats en cartes sur la TV.",
            style = MaterialTheme.typography.bodyMedium,
            color = RemoteColors.TextTertiary,
        )
        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RemoteColors.Surface, RoundedCornerShape(20.dp))
                .border(1.dp, RemoteColors.Outline, RoundedCornerShape(20.dp))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Movie, contentDescription = null, tint = RemoteColors.AccentSoft)
                Spacer(Modifier.width(10.dp))
                Text("TMDB · films, séries, personnes", style = MaterialTheme.typography.titleMedium, color = RemoteColors.TextPrimary, modifier = Modifier.weight(1f))
                if (configured) Icon(Icons.Rounded.CheckCircle, contentDescription = "Configurée", tint = RemoteColors.Success, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (configured) {
                    "Clé enregistrée et chiffrée sur ce téléphone. Disponibilité affichée pour la France (Netflix, Prime Video, Disney+…)."
                } else {
                    "Créez un compte gratuit sur themoviedb.org, puis copiez la « clé d'API » ou le « jeton de lecture » depuis Paramètres → API."
                },
                style = MaterialTheme.typography.bodySmall,
                color = RemoteColors.TextSecondary,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    error = null
                },
                label = { Text(if (configured) "Remplacer la clé" else "Clé d'API TMDB") },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RemoteColors.Accent,
                    unfocusedBorderColor = RemoteColors.Outline,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        checking = true
                        scope.launch {
                            val ok = viewModel.saveTmdbKey(key)
                            checking = false
                            if (ok) key = "" else error = "Clé refusée par TMDB (ou pas de connexion internet)"
                        }
                    },
                    enabled = key.isNotBlank() && !checking,
                    colors = ButtonDefaults.buttonColors(containerColor = RemoteColors.Accent),
                ) {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = RemoteColors.TextPrimary)
                    } else {
                        Text("Vérifier et enregistrer")
                    }
                }
                if (configured) {
                    TextButton(onClick = viewModel::clearTmdbKey) { Text("Supprimer", color = RemoteColors.Danger) }
                }
            }
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.themoviedb.org/settings/api")))
                }
            }) {
                Text("Obtenir une clé sur themoviedb.org", color = RemoteColors.AccentSoft)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Toujours actif : vos applications installées, et la recherche directe dans Netflix, YouTube, Prime Video, Plex et Disney+.",
            style = MaterialTheme.typography.bodySmall,
            color = RemoteColors.TextTertiary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Ce produit utilise l'API TMDB mais n'est ni approuvé ni certifié par TMDB. Disponibilités fournies par JustWatch via TMDB.",
            style = MaterialTheme.typography.bodySmall,
            color = RemoteColors.TextTertiary,
        )
    }
}
