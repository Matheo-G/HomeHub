package com.homehub.remote.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** État du micro. */
@Immutable
sealed interface VoiceState {
    data object Idle : VoiceState

    /**
     * Écoute en cours.
     * @property level niveau sonore normalisé (0..1), pour l'animation.
     * @property partial transcription provisoire.
     */
    data class Listening(val level: Float, val partial: String) : VoiceState

    /** Fin de parole détectée, transcription finale en cours. */
    data class Processing(val partial: String) : VoiceState

    data class Error(val message: String) : VoiceState
}

/**
 * Reconnaissance vocale intégrée (service de reconnaissance du téléphone,
 * en pratique celui de Google), en français, avec transcription en direct.
 *
 * Doit être utilisée depuis le thread principal (exigence de [SpeechRecognizer]).
 */
class VoiceRecognizer(
    context: Context,
    private val onFinalResult: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    private val stateFlow = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = stateFlow.asStateFlow()

    /** Faux si aucun service n'est installé : l'interface bascule alors sur la boîte de dialogue système. */
    val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun start() {
        if (!isAvailable) return
        val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        stateFlow.value = VoiceState.Listening(level = 0f, partial = "")
        runCatching { engine.startListening(recognitionIntent()) }
            .onFailure { stateFlow.value = VoiceState.Error("Micro indisponible") }
    }

    /** Arrête l'écoute : la phrase déjà prononcée est tout de même transcrite. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun cancel() {
        recognizer?.cancel()
        stateFlow.value = VoiceState.Idle
    }

    fun clearError() {
        if (stateFlow.value is VoiceState.Error) stateFlow.value = VoiceState.Idle
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            val current = stateFlow.value
            if (current is VoiceState.Listening) {
                // rmsdB varie environ de -2 (silence) à 10 (voix forte).
                val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                stateFlow.value = current.copy(level = level)
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            val partial = (stateFlow.value as? VoiceState.Listening)?.partial.orEmpty()
            stateFlow.value = VoiceState.Processing(partial)
        }

        override fun onError(error: Int) {
            stateFlow.value = when (error) {
                SpeechRecognizer.ERROR_CLIENT -> VoiceState.Idle
                SpeechRecognizer.ERROR_NO_MATCH -> VoiceState.Error("Je n'ai pas compris, réessayez")
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceState.Error("Je n'ai rien entendu")
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER,
                -> VoiceState.Error("Reconnaissance indisponible : vérifiez la connexion internet")
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceState.Error("Autorisez le micro dans les paramètres")
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceState.Error("Micro occupé, réessayez")
                SpeechRecognizer.ERROR_AUDIO -> VoiceState.Error("Erreur du micro")
                else -> VoiceState.Error("Reconnaissance interrompue")
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results.bestMatch()
            stateFlow.value = VoiceState.Idle
            if (!text.isNullOrBlank()) onFinalResult(text) else stateFlow.value = VoiceState.Error("Je n'ai pas compris, réessayez")
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults.bestMatch() ?: return
            when (val current = stateFlow.value) {
                is VoiceState.Listening -> stateFlow.value = current.copy(partial = text)
                is VoiceState.Processing -> stateFlow.value = current.copy(partial = text)
                else -> Unit
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun Bundle?.bestMatch(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()

    companion object {
        /** Intention de reconnaissance (aussi utilisée par la boîte de dialogue de secours). */
        fun recognitionIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parlez à votre TV")
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_200L)
        }
    }
}
