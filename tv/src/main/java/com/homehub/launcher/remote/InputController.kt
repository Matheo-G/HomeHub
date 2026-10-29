package com.homehub.launcher.remote

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import com.homehub.launcher.utils.AppLauncher
import com.homehub.protocol.RemoteKey

/**
 * Traduit les touches de la télécommande en actions sur la TV.
 *
 * Stratégie par famille de touches :
 *  - médias (lecture, suivant…) : diffusés à la session média active,
 *    fonctionne dans toutes les applications sans autorisation ;
 *  - volume : réglé directement, avec l'indicateur système ;
 *  - Accueil : ramène HomeHub ;
 *  - navigation (flèches, OK, Retour) : injectée dans HomeHub s'il est affiché,
 *    sinon confiée au service d'accessibilité (« contrôle complet »).
 *
 * Doit être appelé sur le thread principal.
 */
class InputController(
    context: Context,
    private val appLauncher: AppLauncher,
) {
    private val audioManager: AudioManager? = context.applicationContext.getSystemService(AudioManager::class.java)

    /** Résultat d'un appui : `false` si la touche n'a pas pu être appliquée. */
    fun press(key: RemoteKey): Boolean = when (key) {
        RemoteKey.HOME -> appLauncher.openHomeHub()

        RemoteKey.PLAY_PAUSE -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        RemoteKey.NEXT -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        RemoteKey.PREVIOUS -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        RemoteKey.REWIND -> mediaKey(KeyEvent.KEYCODE_MEDIA_REWIND)
        RemoteKey.FAST_FORWARD -> mediaKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)

        RemoteKey.VOLUME_UP -> volume(AudioManager.ADJUST_RAISE)
        RemoteKey.VOLUME_DOWN -> volume(AudioManager.ADJUST_LOWER)
        RemoteKey.MUTE -> volume(AudioManager.ADJUST_TOGGLE_MUTE)

        RemoteKey.UP, RemoteKey.DOWN, RemoteKey.LEFT, RemoteKey.RIGHT,
        RemoteKey.CENTER, RemoteKey.BACK -> navigate(key)
    }

    /** Vrai si la navigation fonctionne en dehors de HomeHub. */
    val hasFullControl: Boolean get() = AccessibilityBridge.service != null

    /**
     * Saisit du texte dans le champ actif de l'application au premier plan.
     * HomeHub n'ayant pas de champ de saisie, nécessite le contrôle complet.
     */
    fun typeText(text: String): Boolean {
        if (LauncherForeground.isResumed) return false
        return AccessibilityBridge.service?.setText(text) ?: false
    }

    /** Vrai si un champ de saisie a le focus dans une autre application. */
    fun hasFocusedTextField(): Boolean =
        !LauncherForeground.isResumed && AccessibilityBridge.service?.hasFocusedTextField() == true

    private fun navigate(key: RemoteKey): Boolean {
        if (LauncherForeground.isResumed) {
            return RemoteKeyBus.send(key.toKeyCode())
        }
        return AccessibilityBridge.service?.navigate(key) ?: false
    }

    private fun mediaKey(keyCode: Int): Boolean {
        val manager = audioManager ?: return false
        val time = SystemClock.uptimeMillis()
        manager.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0))
        manager.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, keyCode, 0))
        return true
    }

    private fun volume(direction: Int): Boolean {
        val manager = audioManager ?: return false
        return runCatching {
            manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        }.isSuccess
    }

    private fun RemoteKey.toKeyCode(): Int = when (this) {
        RemoteKey.UP -> KeyEvent.KEYCODE_DPAD_UP
        RemoteKey.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        RemoteKey.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        RemoteKey.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        RemoteKey.CENTER -> KeyEvent.KEYCODE_DPAD_CENTER
        RemoteKey.BACK -> KeyEvent.KEYCODE_BACK
        else -> KeyEvent.KEYCODE_UNKNOWN
    }
}
