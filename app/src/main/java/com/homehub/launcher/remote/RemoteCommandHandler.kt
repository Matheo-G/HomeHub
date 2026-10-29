package com.homehub.launcher.remote

import android.content.Context
import android.os.Build
import android.widget.Toast
import com.homehub.launcher.MainActivity
import com.homehub.launcher.data.models.AppInfo
import com.homehub.launcher.data.repository.AppsRepository
import com.homehub.launcher.data.repository.RecentAppsRepository
import com.homehub.launcher.data.source.FavoritesCatalog
import com.homehub.launcher.utils.AppLauncher
import com.homehub.protocol.ClientMessage
import com.homehub.protocol.ErrorCode
import com.homehub.protocol.Protocol
import com.homehub.protocol.RemoteApp
import com.homehub.protocol.RemoteKey
import com.homehub.protocol.ServerMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Logique d'une session de télécommande : authentification, puis exécution
 * des commandes reçues. Indépendante du transport ([RemoteServer]).
 */
class RemoteCommandHandler(
    context: Context,
    private val pairingManager: PairingManager,
    private val appsRepository: AppsRepository,
    private val recentAppsRepository: RecentAppsRepository,
    private val appLauncher: AppLauncher,
    private val remoteHub: RemoteHub,
    private val boxName: () -> String,
) {
    private val appContext: Context = context.applicationContext
    private val input = InputController(appContext, appLauncher)
    private val interpreter = VoiceCommandInterpreter()

    /** Traite un message et renvoie les réponses à transmettre au téléphone. */
    suspend fun handle(session: RemoteSession, message: ClientMessage): List<ServerMessage> = when (message) {
        is ClientMessage.Hello -> onHello(session, message)
        is ClientMessage.Auth -> onAuth(session, message)
        is ClientMessage.Pair -> onPair(session, message)
        ClientMessage.Ping -> listOf(ServerMessage.Pong)
        else ->
            if (!session.authenticated) {
                listOf(ServerMessage.Error(ErrorCode.UNAUTHORIZED, "Téléphone non appairé"))
            } else {
                execute(session, message)
            }
    }

    // --- Session ----------------------------------------------------------

    private suspend fun onHello(session: RemoteSession, hello: ClientMessage.Hello): List<ServerMessage> {
        session.deviceId = hello.deviceId
        session.deviceName = hello.deviceName
        val info = ServerMessage.Info(pairingManager.boxId(), boxName(), Build.VERSION.SDK_INT)
        if (hello.version != Protocol.VERSION) {
            return listOf(
                info,
                ServerMessage.Error(
                    ErrorCode.INCOMPATIBLE_VERSION,
                    "Mettez à jour HomeHub et HomeHub Remote vers la même version.",
                ),
            )
        }
        return listOf(info)
    }

    private suspend fun onAuth(session: RemoteSession, auth: ClientMessage.Auth): List<ServerMessage> {
        val deviceId = session.deviceId ?: return listOf(ServerMessage.Error(ErrorCode.BAD_REQUEST, "Hello attendu"))
        return if (pairingManager.authenticate(deviceId, auth.token)) {
            session.authenticated = true
            listOf(ServerMessage.Welcome(input.hasFullControl))
        } else {
            listOf(ServerMessage.PairingRequired(pairingManager.isPairingOpen))
        }
    }

    private suspend fun onPair(session: RemoteSession, pair: ClientMessage.Pair): List<ServerMessage> {
        val deviceId = session.deviceId ?: return listOf(ServerMessage.Error(ErrorCode.BAD_REQUEST, "Hello attendu"))
        return when (val outcome = pairingManager.pair(deviceId, session.deviceName, pair.pin)) {
            is PairOutcome.Success -> {
                session.authenticated = true
                listOf(ServerMessage.Paired(outcome.token), ServerMessage.Welcome(input.hasFullControl))
            }
            PairOutcome.WrongPin -> listOf(ServerMessage.Error(ErrorCode.WRONG_PIN, "Code incorrect"))
            PairOutcome.PairingClosed -> listOf(
                ServerMessage.Error(
                    ErrorCode.PAIRING_CLOSED,
                    "Sur la TV, ouvrez Paramètres → Associer un téléphone",
                ),
            )
            PairOutcome.TooManyAttempts -> listOf(
                ServerMessage.Error(ErrorCode.TOO_MANY_ATTEMPTS, "Trop d'essais : un nouveau code est affiché sur la TV"),
            )
        }
    }

    // --- Commandes --------------------------------------------------------

    private suspend fun execute(session: RemoteSession, message: ClientMessage): List<ServerMessage> = when (message) {
        is ClientMessage.Key -> onKey(session, message.key)
        is ClientMessage.Voice -> listOf(onVoice(message.query))
        is ClientMessage.Text -> listOf(onText(message.text))
        is ClientMessage.Launch -> listOf(onLaunch(message.packageName))
        ClientMessage.Apps -> listOf(ServerMessage.Apps(remoteApps()))
        is ClientMessage.Icon -> listOf(ServerMessage.Icon(message.packageName, appsRepository.loadIconPng(message.packageName)))
        else -> emptyList()
    }

    private suspend fun onKey(session: RemoteSession, key: RemoteKey): List<ServerMessage> {
        val applied = withContext(Dispatchers.Main) { input.press(key) }
        if (applied) return emptyList()
        // Prévient une seule fois toutes les 20 s, pour ne pas inonder le téléphone.
        val now = System.currentTimeMillis()
        if (now - session.lastFullControlHint < HINT_INTERVAL_MS) return emptyList()
        session.lastFullControlHint = now
        return listOf(
            ServerMessage.Result(
                ok = false,
                message = "Pour piloter les autres applications, activez le contrôle complet " +
                    "(TV : Paramètres HomeHub → Contrôle complet).",
            ),
        )
    }

    private suspend fun onText(text: String): ServerMessage {
        val typed = withContext(Dispatchers.Main) { input.typeText(text) }
        return if (typed) {
            ServerMessage.Result(true, "Texte envoyé")
        } else {
            ServerMessage.Result(false, textUnavailableMessage())
        }
    }

    private suspend fun onLaunch(packageName: String): ServerMessage {
        val label = installedApps().firstOrNull { it.packageName == packageName }?.label ?: packageName
        val launched = withContext(Dispatchers.Main) { appLauncher.launchApp(packageName) }
        if (launched) recentAppsRepository.recordLaunch(packageName)
        return ServerMessage.Result(launched, if (launched) "Ouverture de $label" else "Impossible d'ouvrir $label")
    }

    /** Interprète puis exécute une phrase ; affiche aussi le résultat sur la TV. */
    private suspend fun onVoice(query: String): ServerMessage {
        val action = interpreter.interpret(query, installedApps())
        val result = withContext(Dispatchers.Main) { perform(action) }
        showOnTv(RemoteFeedback(query = query, message = result.message, ok = result.ok))
        return result
    }

    private suspend fun perform(action: VoiceAction): ServerMessage.Result = when (action) {
        is VoiceAction.OpenApp -> {
            val ok = appLauncher.launchApp(action.app.packageName)
            if (ok) recentAppsRepository.recordLaunch(action.app.packageName)
            result(ok, "Ouverture de ${action.app.label}", "Impossible d'ouvrir ${action.app.label}")
        }

        is VoiceAction.Search -> search(action.query, action.app)

        is VoiceAction.PlayMedia -> {
            val app = action.app
            when {
                appLauncher.playFromSearch(app?.packageName, action.query) ->
                    result(true, "Lecture de « ${action.query} »" + (app?.let { " sur ${it.label}" } ?: ""), "")
                else -> search(action.query, app)
            }
        }

        is VoiceAction.Press -> {
            var ok = true
            repeat(action.times) { index ->
                ok = input.press(action.key) && ok
                if (index < action.times - 1) delay(KEY_REPEAT_DELAY_MS)
            }
            if (ok) {
                result(true, action.label, "")
            } else {
                result(false, "", "« ${action.label} » nécessite le contrôle complet (Paramètres HomeHub)")
            }
        }

        VoiceAction.GoHome -> result(appLauncher.openHomeHub(), "Accueil", "Impossible d'afficher l'accueil")
        VoiceAction.OpenHomeHubSettings ->
            result(appLauncher.openHomeHub(MainActivity.DESTINATION_SETTINGS), "Paramètres HomeHub", "Impossible d'ouvrir les paramètres")
        VoiceAction.OpenAllApps ->
            result(appLauncher.openHomeHub(MainActivity.DESTINATION_ALL_APPS), "Toutes les applications", "Impossible d'ouvrir la liste")
        VoiceAction.OpenSystemSettings ->
            result(appLauncher.openSystemSettings(), "Paramètres Android TV", "Impossible d'ouvrir les paramètres système")

        is VoiceAction.FreeText ->
            // Un champ de saisie actif ? On dicte dedans. Sinon, recherche globale.
            if (input.hasFocusedTextField() && input.typeText(action.text)) {
                result(true, "Saisi : « ${action.text} »", "")
            } else {
                search(action.text, null)
            }
    }

    /** Recherche dans l'application demandée, sinon recherche globale de la TV. */
    private suspend fun search(query: String, app: AppInfo?): ServerMessage.Result {
        if (app != null) {
            if (appLauncher.searchInApp(app.packageName, query)) {
                return result(true, "Recherche de « $query » sur ${app.label}", "")
            }
            // L'application ne gère pas la recherche externe : on l'ouvre, et on
            // tente de saisir la requête si son champ de recherche apparaît.
            if (appLauncher.launchApp(app.packageName)) {
                recentAppsRepository.recordLaunch(app.packageName)
                return result(true, "${app.label} ouvert : utilisez sa recherche puis le clavier du téléphone", "")
            }
        }
        if (input.hasFocusedTextField() && input.typeText(query)) {
            return result(true, "Saisi : « $query »", "")
        }
        return result(appLauncher.globalSearch(query), "Recherche de « $query »", "Recherche indisponible sur cette TV")
    }

    // --- Outils -----------------------------------------------------------

    private suspend fun installedApps(): List<AppInfo> =
        appsRepository.installedApps.value ?: appsRepository.loadApps()

    private suspend fun remoteApps(): List<RemoteApp> {
        val favorites = FavoritesCatalog.knownPackages
        return installedApps().map { RemoteApp(it.packageName, it.label, it.packageName in favorites) }
    }

    private fun textUnavailableMessage(): String = when {
        LauncherForeground.isResumed -> "Ouvrez d'abord une application avec un champ de recherche"
        !input.hasFullControl -> "Activez le contrôle complet sur la TV pour saisir du texte"
        else -> "Aucun champ de saisie actif sur la TV"
    }

    private fun result(ok: Boolean, success: String, failure: String): ServerMessage.Result =
        ServerMessage.Result(ok, if (ok) success else failure)

    /** Retour visuel : bandeau dans HomeHub, sinon notification éphémère par-dessus l'application. */
    private suspend fun showOnTv(feedback: RemoteFeedback) {
        if (LauncherForeground.isResumed) {
            remoteHub.showFeedback(feedback)
        } else {
            withContext(Dispatchers.Main) {
                val text = feedback.query?.let { "🎙 « $it »\n${feedback.message}" } ?: feedback.message
                Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private companion object {
        const val HINT_INTERVAL_MS = 20_000L
        const val KEY_REPEAT_DELAY_MS = 90L
    }
}

/** État d'une connexion téléphone. */
class RemoteSession {
    @Volatile var deviceId: String? = null
    @Volatile var deviceName: String = "Téléphone"
    @Volatile var authenticated: Boolean = false
    @Volatile var lastFullControlHint: Long = 0L
}
