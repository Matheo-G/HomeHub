package com.homehub.launcher.command

import android.content.Context
import android.widget.Toast
import com.homehub.launcher.MainActivity
import com.homehub.launcher.data.repository.RecentAppsRepository
import com.homehub.launcher.remote.InputController
import com.homehub.launcher.remote.LauncherForeground
import com.homehub.launcher.remote.RemoteFeedback
import com.homehub.launcher.remote.RemoteHub
import com.homehub.launcher.utils.AppLauncher
import com.homehub.protocol.Destination
import com.homehub.protocol.ErrorCodes
import com.homehub.protocol.OpenIntent
import com.homehub.protocol.ToastKind

/*
 * Handlers par domaine. Chacun traduit une commande en appels système et
 * renvoie un CommandResult explicite ; aucun ne lève d'exception vers l'appelant.
 * Ils s'exécutent sur le thread principal (garanti par CommandDispatcher).
 */

/** Touches et saisie de texte. */
class InputHandler(private val input: InputController) {

    fun press(command: TvCommand.PressKey): CommandResult =
        if (input.press(command.key)) {
            CommandResult.Done()
        } else {
            CommandResult.Failed(
                ErrorCodes.FULL_CONTROL_REQUIRED,
                "Activez le contrôle complet (TV : Paramètres HomeHub → Contrôle complet) pour piloter les autres applications",
            )
        }

    fun type(command: TvCommand.TypeText): CommandResult = when {
        input.typeText(command.text) -> CommandResult.Done()
        !LauncherForeground.isResumed && !input.hasFullControl -> CommandResult.Failed(
            ErrorCodes.FULL_CONTROL_REQUIRED,
            "Activez le contrôle complet sur la TV pour saisir du texte",
        )
        else -> CommandResult.Failed(ErrorCodes.NO_TEXT_FIELD, "Aucun champ de saisie actif sur la TV")
    }
}

/** Ouverture d'applications, recherches et lectures déléguées aux applications. */
class AppHandler(
    private val appLauncher: AppLauncher,
    private val recentApps: RecentAppsRepository,
) {
    suspend fun launch(command: TvCommand.LaunchApp): CommandResult {
        if (!appLauncher.launchApp(command.packageName)) return notInstalled(command.packageName)
        recentApps.recordLaunch(command.packageName)
        return CommandResult.Done()
    }

    /**
     * Essaie l'intention demandée puis se replie, dans l'ordre, sur la recherche
     * dans l'application et sur sa simple ouverture (docs/api/local-api.md §4.2).
     */
    suspend fun open(command: TvCommand.OpenApp): CommandResult {
        val packageName = command.packageName
        val query = command.query?.trim().orEmpty()
        val result = when (command.intent) {
            OpenIntent.VIEW -> when {
                command.uri != null && appLauncher.openUri(packageName, command.uri) -> CommandResult.Done()
                packageName != null && appLauncher.launchApp(packageName) -> CommandResult.Done(FALLBACK_LAUNCHED)
                else -> notInstalled(packageName)
            }

            OpenIntent.SEARCH -> when {
                query.isEmpty() -> CommandResult.Failed(ErrorCodes.INVALID_PARAMS, "Recherche vide")
                packageName == null ->
                    if (appLauncher.globalSearch(query)) {
                        CommandResult.Done()
                    } else {
                        CommandResult.Failed(ErrorCodes.UNSUPPORTED, "Recherche indisponible sur cette TV")
                    }
                appLauncher.searchInApp(packageName, query) -> CommandResult.Done()
                appLauncher.launchApp(packageName) -> CommandResult.Done(FALLBACK_LAUNCHED)
                else -> notInstalled(packageName)
            }

            OpenIntent.PLAY_FROM_SEARCH -> when {
                query.isEmpty() -> CommandResult.Failed(ErrorCodes.INVALID_PARAMS, "Lecture sans contenu")
                appLauncher.playFromSearch(packageName, query) -> CommandResult.Done()
                packageName != null && appLauncher.searchInApp(packageName, query) -> CommandResult.Done(FALLBACK_SEARCH)
                packageName != null && appLauncher.launchApp(packageName) -> CommandResult.Done(FALLBACK_LAUNCHED)
                packageName == null && appLauncher.globalSearch(query) -> CommandResult.Done(FALLBACK_SEARCH)
                else -> notInstalled(packageName)
            }
        }
        if (result is CommandResult.Done && packageName != null) recentApps.recordLaunch(packageName)
        return result
    }

    fun openStore(command: TvCommand.OpenStore): CommandResult =
        if (appLauncher.openStorePage(command.packageName)) {
            CommandResult.Done()
        } else {
            CommandResult.Failed(ErrorCodes.UNSUPPORTED, "Play Store indisponible")
        }

    private fun notInstalled(packageName: String?): CommandResult =
        CommandResult.Failed(ErrorCodes.APP_NOT_INSTALLED, "Application non installée" + (packageName?.let { " : $it" } ?: ""))

    private companion object {
        const val FALLBACK_LAUNCHED = "launched"
        const val FALLBACK_SEARCH = "search"
    }
}

/** Écrans HomeHub et système. */
class NavigationHandler(private val appLauncher: AppLauncher) {
    fun navigate(command: TvCommand.Navigate): CommandResult {
        val ok = when (command.destination) {
            Destination.HOME -> appLauncher.openHomeHub()
            Destination.ALL_APPS -> appLauncher.openHomeHub(MainActivity.DESTINATION_ALL_APPS)
            Destination.SETTINGS -> appLauncher.openHomeHub(MainActivity.DESTINATION_SETTINGS)
            Destination.ANDROID_SETTINGS -> appLauncher.openSystemSettings()
        }
        return if (ok) CommandResult.Done() else CommandResult.Failed(ErrorCodes.UNSUPPORTED, "Écran indisponible")
    }
}

/** Retours visuels : bandeau dans HomeHub, message système par-dessus les autres applications. */
class FeedbackHandler(
    context: Context,
    private val remoteHub: RemoteHub,
) {
    private val appContext = context.applicationContext

    fun toast(command: TvCommand.ShowToast): CommandResult {
        val ok = command.kind != ToastKind.ERROR
        if (LauncherForeground.isResumed) {
            remoteHub.showFeedback(RemoteFeedback(query = command.title, message = command.message, ok = ok))
        } else {
            val text = command.title?.let { "🎙 « $it »\n${command.message}" } ?: command.message
            Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show()
        }
        return CommandResult.Done()
    }
}
