package com.homehub.remote

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homehub.remote.data.ConnectionState
import com.homehub.remote.ui.components.AmbientGlow
import com.homehub.remote.ui.screens.ConnectScreen
import com.homehub.remote.ui.screens.PairScreen
import com.homehub.remote.ui.screens.RemoteMainScreen
import com.homehub.remote.ui.theme.HomeHubRemoteTheme
import com.homehub.remote.ui.theme.RemoteColors
import com.homehub.remote.viewmodel.RemoteScreen
import com.homehub.remote.viewmodel.RemoteViewModel
import com.homehub.remote.voice.VoiceRecognizer
import com.homehub.remote.voice.VoiceState

/**
 * Activité unique de HomeHub Remote.
 * La connexion à la TV vit au rythme de l'écran : ouverte au premier plan,
 * fermée en arrière-plan.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: RemoteViewModel by viewModels { RemoteViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HomeHubRemoteTheme {
                RemoteRoot(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onStop() {
        viewModel.onBackground()
        super.onStop()
    }
}

@Composable
private fun RemoteRoot(viewModel: RemoteViewModel) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Repli : boîte de dialogue vocale du système si aucun service intégré n'est disponible.
    val systemVoice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(viewModel::sendVoice)
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoice()
    }

    val onMicClick: () -> Unit = {
        when {
            voice is VoiceState.Listening -> viewModel.stopVoice()
            voice is VoiceState.Processing -> viewModel.cancelVoice()
            !viewModel.inlineVoiceAvailable ->
                runCatching { systemVoice.launch(VoiceRecognizer.recognitionIntent()) }
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> viewModel.startVoice()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RemoteColors.Background),
    ) {
        AmbientGlow()
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            label = "screen",
        ) { current ->
            when (current) {
                RemoteScreen.Connect -> ConnectScreen(viewModel)
                RemoteScreen.Pair -> {
                    val pairing = connection as? ConnectionState.NeedsPairing
                    if (pairing != null) {
                        PairScreen(
                            state = pairing,
                            onSubmit = viewModel::submitPin,
                            onCancel = viewModel::changeBox,
                        )
                    }
                }
                RemoteScreen.Remote -> RemoteMainScreen(viewModel = viewModel, onMicClick = onMicClick)
            }
        }
    }
}
