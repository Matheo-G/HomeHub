package com.homehub.launcher.command

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.homehub.launcher.cards.CardsStore
import com.homehub.launcher.remote.LauncherForeground
import com.homehub.launcher.utils.AppLauncher
import com.homehub.protocol.Card
import com.homehub.protocol.DismissReason
import com.homehub.protocol.ErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * Cartes envoyées par le téléphone (docs/adr/0008).
 *
 * Les images arrivent encodées en Base64 : elles sont décodées hors du thread
 * principal, plafonnées en taille, puis transmises prêtes à dessiner au
 * [CardsStore]. Afficher des cartes ramène HomeHub au premier plan.
 */
class CardHandler(
    private val store: CardsStore,
    private val appLauncher: AppLauncher,
) {
    suspend fun show(command: TvCommand.ShowCards): CommandResult {
        val images = decodeImages(command.surface.cards)
        store.show(command.surface, images)
        if (!LauncherForeground.isResumed) appLauncher.openHomeHub()
        return CommandResult.Done()
    }

    suspend fun update(command: TvCommand.UpdateCards): CommandResult {
        val images = decodeImages(command.patch.replaceCards + command.patch.appendCards)
        return if (store.update(command.surfaceId, command.patch, images)) {
            CommandResult.Done()
        } else {
            CommandResult.Failed(ErrorCodes.INVALID_PARAMS, "Surface fermée : ${command.surfaceId}")
        }
    }

    fun dismiss(command: TvCommand.DismissCards): CommandResult {
        store.dismiss(command.surfaceId, DismissReason.REMOTE)
        return CommandResult.Done()
    }

    private suspend fun decodeImages(cards: List<Card>): Map<String, ImageBitmap> =
        withContext(Dispatchers.Default) {
            cards.mapNotNull { card ->
                val data = card.image?.data ?: return@mapNotNull null
                if (data.length > MAX_IMAGE_BASE64) return@mapNotNull null
                runCatching {
                    val bytes = Base64.getDecoder().decode(data)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bitmap ->
                        bitmap.prepareToDraw()
                        card.id to bitmap.asImageBitmap()
                    }
                }.getOrNull()
            }.toMap()
        }

    private companion object {
        /** ≈ 60 Ko d'image (docs/api/local-api.md). */
        const val MAX_IMAGE_BASE64 = 82_000
    }
}
