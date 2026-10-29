package com.homehub.launcher.remote

import com.homehub.launcher.command.CommandDispatcher
import com.homehub.launcher.command.CommandOrigin
import com.homehub.launcher.command.CommandResult
import com.homehub.launcher.command.TvCommand
import com.homehub.launcher.data.models.AppInfo
import com.homehub.launcher.data.repository.AppsRepository
import com.homehub.launcher.data.source.FavoritesCatalog
import com.homehub.protocol.AppEntry
import com.homehub.protocol.AppListResult
import com.homehub.protocol.Applied
import com.homehub.protocol.AuthResult
import com.homehub.protocol.BoxInfo
import com.homehub.protocol.Capabilities
import com.homehub.protocol.Empty
import com.homehub.protocol.ErrorCodes
import com.homehub.protocol.HelloResult
import com.homehub.protocol.IconResult
import com.homehub.protocol.JsonRpc
import com.homehub.protocol.KeyAction
import com.homehub.protocol.LaunchResult
import com.homehub.protocol.Method
import com.homehub.protocol.Methods
import com.homehub.protocol.NavigateResult
import com.homehub.protocol.OpenResult
import com.homehub.protocol.PairResult
import com.homehub.protocol.Protocol
import com.homehub.protocol.RpcException
import com.homehub.protocol.RpcMessage
import kotlinx.coroutines.CancellationException
import java.util.Base64

/**
 * Routeur JSON-RPC de la TV : traduit chaque méthode de l'API locale en
 * commande pour le [CommandDispatcher], ou en lecture (liste et icônes
 * d'applications). Aucune décision n'est prise ici.
 *
 * Règles transverses appliquées à chaque message :
 *  - limitation de débit par session ;
 *  - authentification obligatoire hors méthodes `session.*` publiques ;
 *  - toute erreur devient une réponse JSON-RPC explicite.
 */
class RpcRouter(
    private val dispatcher: CommandDispatcher,
    private val pairingManager: PairingManager,
    private val appsRepository: AppsRepository,
    private val boxInfo: suspend () -> BoxInfo,
    private val fullControl: () -> Boolean,
) {
    private val routes = HashMap<String, suspend (RpcSession, RpcMessage.Call) -> String?>()

    init {
        // --- Session ---------------------------------------------------------
        on(Methods.SessionHello) { session, params ->
            session.deviceId = params.client.id
            session.deviceName = params.client.name.ifBlank { "Téléphone" }
            if (params.protocol != Protocol.VERSION) {
                throw RpcException(
                    ErrorCodes.INCOMPATIBLE_PROTOCOL,
                    "Mettez à jour HomeHub et HomeHub Remote vers la même version",
                )
            }
            HelloResult(
                protocol = Protocol.VERSION,
                authRequired = true,
                pairingOpen = pairingManager.isPairingOpen,
                box = boxInfo(),
                capabilities = listOf(Capabilities.INPUT_FULL_CONTROL, Capabilities.APP_OPEN, Capabilities.UI_TOAST),
            )
        }
        on(Methods.SessionAuth) { session, params ->
            val deviceId = session.requireDevice()
            if (!pairingManager.authenticate(deviceId, params.token)) {
                throw RpcException(ErrorCodes.UNKNOWN_TOKEN, "Téléphone inconnu de cette TV : associez-le de nouveau")
            }
            session.authenticated = true
            AuthResult(fullControl())
        }
        on(Methods.SessionPair) { session, params ->
            val deviceId = session.requireDevice()
            when (val outcome = pairingManager.pair(deviceId, session.deviceName, params.pin)) {
                is PairOutcome.Success -> {
                    session.authenticated = true
                    PairResult(outcome.token)
                }
                PairOutcome.WrongPin -> throw RpcException(ErrorCodes.WRONG_PIN, "Code incorrect")
                PairOutcome.PairingClosed -> throw RpcException(
                    ErrorCodes.PAIRING_CLOSED,
                    "Sur la TV, ouvrez Paramètres → Associer un téléphone",
                )
                PairOutcome.TooManyAttempts -> throw RpcException(
                    ErrorCodes.TOO_MANY_ATTEMPTS,
                    "Trop d'essais : un nouveau code est affiché sur la TV",
                )
            }
        }
        on(Methods.SessionPing) { _, _ -> Empty }

        // --- Entrées ---------------------------------------------------------
        on(Methods.InputKey) { _, params ->
            // Un appui complet est exécuté à l'enfoncement ; le relâchement est sans effet.
            if (params.action == KeyAction.UP) return@on Applied(true)
            execute(TvCommand.PressKey(params.key))
            Applied(true)
        }
        on(Methods.InputText) { _, params ->
            execute(TvCommand.TypeText(params.text))
            Applied(true)
        }

        // --- Applications ----------------------------------------------------
        on(Methods.AppList) { _, _ ->
            val favorites = FavoritesCatalog.knownPackages
            AppListResult(installedApps().map { it.toEntry(it.packageName in favorites) })
        }
        on(Methods.AppIcon) { _, params ->
            val png = appsRepository.loadIconPng(params.packageName)
            IconResult(png?.let { Base64.getEncoder().encodeToString(it) })
        }
        on(Methods.AppLaunch) { _, params ->
            execute(TvCommand.LaunchApp(params.packageName))
            LaunchResult(true)
        }
        on(Methods.AppOpen) { _, params ->
            val done = execute(TvCommand.OpenApp(params.packageName, params.intent, params.uri, params.query))
            OpenResult(opened = true, fallback = done.fallback)
        }

        // --- Interface -------------------------------------------------------
        on(Methods.UiNavigate) { _, params ->
            execute(TvCommand.Navigate(params.destination))
            NavigateResult(true)
        }
        on(Methods.UiToast) { _, params ->
            execute(TvCommand.ShowToast(params.message, params.title, params.kind))
            Empty
        }
    }

    /**
     * Traite une trame reçue ; renvoie la réponse à envoyer (`null` pour une
     * notification ou une réponse du téléphone).
     */
    suspend fun handle(session: RpcSession, text: String): String? {
        val message = try {
            JsonRpc.parse(text)
        } catch (error: RpcException) {
            return JsonRpc.error(null, error)
        }
        val call = message as? RpcMessage.Call ?: return null
        return try {
            if (!session.rateLimiter.tryAcquire()) {
                throw RpcException(ErrorCodes.RATE_LIMITED, "Trop de messages, ralentissez")
            }
            if (!session.authenticated && call.method !in Methods.public) {
                throw RpcException(ErrorCodes.NOT_AUTHENTICATED, "Téléphone non associé")
            }
            val route = routes[call.method]
                ?: throw RpcException(ErrorCodes.METHOD_NOT_FOUND, "Méthode inconnue : ${call.method}")
            route(session, call)
        } catch (error: RpcException) {
            if (call.isNotification) null else JsonRpc.error(call.id, error)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (call.isNotification) {
                null
            } else {
                JsonRpc.error(call.id, RpcException(ErrorCodes.INTERNAL_ERROR, error.message ?: "Erreur interne"))
            }
        }
    }

    /** Enregistre une méthode typée : décodage des paramètres, appel, encodage du résultat. */
    private fun <P, R> on(method: Method<P, R>, block: suspend (RpcSession, P) -> R) {
        routes[method.name] = { session, call ->
            val params = JsonRpc.decodeParams(method.params, call.params)
            val result = block(session, params)
            call.id?.let { JsonRpc.result(it, method.result, result) }
        }
    }

    /** Exécute une commande ; un échec devient une erreur JSON-RPC. */
    private suspend fun execute(command: TvCommand): CommandResult.Done =
        when (val result = dispatcher.dispatch(command, CommandOrigin.REMOTE)) {
            is CommandResult.Done -> result
            is CommandResult.Failed -> throw RpcException(result.code, result.message)
        }

    private suspend fun installedApps(): List<AppInfo> =
        appsRepository.installedApps.value ?: appsRepository.loadApps()

    private fun RpcSession.requireDevice(): String =
        deviceId ?: throw RpcException(ErrorCodes.INVALID_REQUEST, "session.hello attendu")

    private fun AppInfo.toEntry(favorite: Boolean) = AppEntry(packageName, label, favorite, isTvApp)
}

/** État d'une connexion téléphone. */
class RpcSession {
    @Volatile var deviceId: String? = null
    @Volatile var deviceName: String = "Téléphone"
    @Volatile var authenticated: Boolean = false
    val rateLimiter = RateLimiter(Protocol.MAX_MESSAGES_PER_SECOND)
}

/** Fenêtre glissante d'une seconde : au plus [perSecond] messages. */
class RateLimiter(private val perSecond: Int) {
    private var windowStart = 0L
    private var count = 0

    @Synchronized
    fun tryAcquire(now: Long = System.currentTimeMillis()): Boolean {
        if (now - windowStart >= 1_000L) {
            windowStart = now
            count = 0
        }
        count++
        return count <= perSecond
    }
}
