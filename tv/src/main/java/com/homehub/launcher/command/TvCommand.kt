package com.homehub.launcher.command

import com.homehub.protocol.CardPatch
import com.homehub.protocol.CardSurface
import com.homehub.protocol.Destination
import com.homehub.protocol.OpenIntent
import com.homehub.protocol.RemoteKey
import com.homehub.protocol.ToastKind

/**
 * Commande exécutable par la TV.
 *
 * C'est l'unique porte d'entrée des actions de HomeHub TV (docs/architecture.md §5.2) :
 * le serveur réseau traduit chaque méthode de l'API locale en [TvCommand], et
 * l'interface locale émet les mêmes commandes. Aucune commande ne contient de
 * logique de décision : la TV exécute, le téléphone décide (docs/adr/0002).
 */
sealed interface TvCommand {
    data class PressKey(val key: RemoteKey) : TvCommand
    data class TypeText(val text: String) : TvCommand
    data class LaunchApp(val packageName: String) : TvCommand
    data class OpenApp(
        val packageName: String?,
        val intent: OpenIntent,
        val uri: String?,
        val query: String?,
    ) : TvCommand

    /** Fiche Play Store d'une application (favori non installé). */
    data class OpenStore(val packageName: String) : TvCommand
    data class Navigate(val destination: Destination) : TvCommand
    data class ShowToast(val message: String, val title: String?, val kind: ToastKind) : TvCommand

    /** Affiche une surface de cartes (résultats, réponse…) ; ramène HomeHub au premier plan. */
    data class ShowCards(val surface: CardSurface) : TvCommand
    data class UpdateCards(val surfaceId: String, val patch: CardPatch) : TvCommand
    data class DismissCards(val surfaceId: String) : TvCommand
}

/** Provenance d'une commande (journalisation, règles futures d'automatisation). */
enum class CommandOrigin { REMOTE, LOCAL }

/** Issue d'une commande. */
sealed interface CommandResult {
    /**
     * Commande exécutée.
     * @property fallback ce qui a réellement été fait quand l'action demandée n'était
     *                    pas prise en charge (`"launched"`, `"search"`).
     */
    data class Done(val fallback: String? = null) : CommandResult

    /** Échec, avec un code d'erreur de l'API (com.homehub.protocol.ErrorCodes). */
    data class Failed(val code: Int, val message: String) : CommandResult
}
