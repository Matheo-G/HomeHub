package com.homehub.launcher.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import com.homehub.launcher.ui.theme.HubMotion

/**
 * Écrans de HomeHub.
 *
 * @property depth profondeur dans la hiérarchie : détermine le sens de la transition.
 */
enum class Destination(val depth: Int) {
    Home(0),
    AllApps(1),
    Settings(1),
}

/**
 * Pile de navigation minimale, observable par Compose.
 *
 * Trois écrans ne justifient pas navigation-compose ; cette pile suffit, reste
 * entièrement typée, et pourra être remplacée sans toucher aux écrans.
 * L'accueil est toujours en bas de pile : le bouton Retour n'en sort jamais.
 */
@Stable
class Navigator {

    private val backStack = mutableStateListOf(Destination.Home)

    /** Écran affiché. */
    val current: Destination get() = backStack.last()

    /** Vrai si Retour doit revenir à l'écran précédent. */
    val canGoBack: Boolean get() = backStack.size > 1

    fun navigate(destination: Destination) {
        if (current != destination) backStack.add(destination)
    }

    /** Revient d'un écran ; renvoie `false` si l'on est déjà sur l'accueil. */
    fun back(): Boolean {
        if (!canGoBack) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    /** Ouvre un écran depuis l'accueil (commande vocale « paramètres »…). */
    fun openFromHome(destination: Destination) {
        resetToHome()
        navigate(destination)
    }

    /** Bouton Home pressé alors que HomeHub est déjà au premier plan. */
    fun resetToHome() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
}

/**
 * Transition entre écrans : léger zoom + fondu.
 * En avançant, le nouvel écran arrive de légèrement plus loin ; en revenant,
 * l'accueil se rapproche — une profondeur perceptible mais jamais brutale.
 */
fun AnimatedContentTransitionScope<Destination>.screenTransition(): ContentTransform {
    val forward = targetState.depth >= initialState.depth
    val enter = fadeIn(tween(HubMotion.ScreenEnterMillis, delayMillis = 60, easing = FastOutSlowInEasing)) +
        scaleIn(
            initialScale = if (forward) 0.96f else 1.04f,
            animationSpec = tween(HubMotion.ScreenEnterMillis, easing = FastOutSlowInEasing),
        )
    val exit = fadeOut(tween(HubMotion.ScreenExitMillis, easing = FastOutSlowInEasing)) +
        scaleOut(
            targetScale = if (forward) 1.03f else 0.97f,
            animationSpec = tween(HubMotion.ScreenExitMillis, easing = FastOutSlowInEasing),
        )
    return enter togetherWith exit
}
