package com.homehub.launcher.command

import android.os.SystemClock
import android.util.Log
import com.homehub.protocol.ErrorCodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Couche de commandes unique de HomeHub TV.
 *
 * Toute action — reçue du téléphone ou déclenchée par l'interface — passe
 * par [dispatch], qui :
 *  - exécute la commande sur le thread principal (exigence des API Android d'activité) ;
 *  - route vers le handler du domaine ;
 *  - convertit toute exception en [CommandResult.Failed] : un launcher ne plante pas ;
 *  - journalise origine, durée et résultat.
 *
 * Ajouter une commande : un cas dans [TvCommand], une branche ici, un handler.
 */
class CommandDispatcher(
    private val input: InputHandler,
    private val apps: AppHandler,
    private val navigation: NavigationHandler,
    private val feedback: FeedbackHandler,
) {
    suspend fun dispatch(command: TvCommand, origin: CommandOrigin): CommandResult =
        withContext(Dispatchers.Main.immediate) {
            val start = SystemClock.elapsedRealtime()
            val result = try {
                when (command) {
                    is TvCommand.PressKey -> input.press(command)
                    is TvCommand.TypeText -> input.type(command)
                    is TvCommand.LaunchApp -> apps.launch(command)
                    is TvCommand.OpenApp -> apps.open(command)
                    is TvCommand.OpenStore -> apps.openStore(command)
                    is TvCommand.Navigate -> navigation.navigate(command)
                    is TvCommand.ShowToast -> feedback.toast(command)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                CommandResult.Failed(ErrorCodes.INTERNAL_ERROR, error.message ?: "Erreur interne")
            }
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "$origin ${command::class.simpleName} → $result (${SystemClock.elapsedRealtime() - start} ms)")
            }
            result
        }

    private companion object {
        const val TAG = "HomeHub"
    }
}
