package com.homehub.remote.planner

import com.homehub.intent.AppRef
import com.homehub.intent.UserIntent
import com.homehub.protocol.Capabilities
import com.homehub.protocol.ErrorCodes
import com.homehub.protocol.KeyParams
import com.homehub.protocol.LaunchParams
import com.homehub.protocol.Methods
import com.homehub.protocol.NavigateParams
import com.homehub.protocol.OpenIntent
import com.homehub.protocol.OpenParams
import com.homehub.protocol.RpcException
import com.homehub.protocol.TextParams
import com.homehub.protocol.ToastKind
import com.homehub.protocol.ToastParams
import com.homehub.remote.data.ConnectionState
import com.homehub.remote.data.RemoteClient
import kotlinx.coroutines.delay

/** Résultat d'une intention exécutée, affiché sur le téléphone et sur la TV. */
data class Outcome(val ok: Boolean, val message: String)

/**
 * Planificateur : traduit une [UserIntent] en commandes de l'API locale.
 *
 * C'est ici — sur le téléphone — que se prennent les décisions
 * (docs/adr/0002) : quelle méthode appeler, quel repli tenter, quel message
 * afficher. La TV se contente d'exécuter.
 */
class IntentExecutor(
    private val client: RemoteClient,
    private val search: SearchCoordinator,
) {

    suspend fun execute(intent: UserIntent): Outcome = try {
        when (intent) {
            is UserIntent.LaunchApp -> launch(intent.app)
            is UserIntent.Search -> search(intent.query, intent.app)
            is UserIntent.PlayMedia -> play(intent.query, intent.app)
            is UserIntent.Key -> keys(intent)
            is UserIntent.Navigate -> {
                client.call(Methods.UiNavigate, NavigateParams(intent.destination))
                Outcome(true, intent.label)
            }
            is UserIntent.FreeText -> freeText(intent.text)
        }
    } catch (error: RpcException) {
        Outcome(false, error.message)
    }

    /** Affiche le résultat sur la TV, sous la phrase prononcée. */
    fun announce(query: String, outcome: Outcome) {
        client.notify(
            Methods.UiToast,
            ToastParams(
                message = outcome.message,
                title = query,
                kind = if (outcome.ok) ToastKind.SUCCESS else ToastKind.ERROR,
            ),
        )
    }

    private suspend fun launch(app: AppRef): Outcome {
        client.call(Methods.AppLaunch, LaunchParams(app.packageName))
        return Outcome(true, "Ouverture de ${app.label}")
    }

    private suspend fun search(query: String, app: AppRef?): Outcome {
        if (app != null) {
            val result = client.call(Methods.AppOpen, OpenParams(app.packageName, OpenIntent.SEARCH, query = query))
            return if (result.fallback == null) {
                Outcome(true, "Recherche de « $query » sur ${app.label}")
            } else {
                Outcome(true, "${app.label} ouvert : utilisez sa recherche et le clavier du téléphone")
            }
        }
        return universalSearch(query)
    }

    /**
     * Recherche universelle en cartes sur la TV (lot 3.3/3.4) ; recherche
     * globale du système si la TV ne sait pas encore afficher de cartes.
     */
    private suspend fun universalSearch(query: String): Outcome {
        val connected = client.state.value as? ConnectionState.Connected
        if (connected != null && Capabilities.CARDS in connected.capabilities) return search.search(query)
        client.call(Methods.AppOpen, OpenParams(packageName = null, intent = OpenIntent.SEARCH, query = query))
        return Outcome(true, "Recherche de « $query »")
    }

    private suspend fun play(query: String, app: AppRef?): Outcome {
        val result = client.call(
            Methods.AppOpen,
            OpenParams(app?.packageName, OpenIntent.PLAY_FROM_SEARCH, query = query),
        )
        val where = app?.let { " sur ${it.label}" }.orEmpty()
        return when (result.fallback) {
            null -> Outcome(true, "Lecture de « $query »$where")
            "search" -> Outcome(true, "Recherche de « $query »$where")
            else -> Outcome(true, "${app?.label ?: "Application"} ouverte : lancez « $query » depuis l'application")
        }
    }

    private suspend fun keys(intent: UserIntent.Key): Outcome {
        repeat(intent.times) { index ->
            client.call(Methods.InputKey, KeyParams(intent.key))
            if (index < intent.times - 1) delay(KEY_REPEAT_DELAY_MS)
        }
        return Outcome(true, intent.label)
    }

    /**
     * Texte libre : saisi dans le champ actif de la TV s'il y en a un
     * (l'utilisateur dicte dans la recherche de Netflix…), sinon recherche universelle.
     */
    private suspend fun freeText(text: String): Outcome {
        try {
            client.call(Methods.InputText, TextParams(text))
            return Outcome(true, "Saisi : « $text »")
        } catch (error: RpcException) {
            if (error.code != ErrorCodes.NO_TEXT_FIELD && error.code != ErrorCodes.FULL_CONTROL_REQUIRED) throw error
        }
        return universalSearch(text)
    }

    private companion object {
        const val KEY_REPEAT_DELAY_MS = 90L
    }
}
