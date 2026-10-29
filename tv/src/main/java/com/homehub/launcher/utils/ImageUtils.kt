package com.homehub.launcher.utils

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt

/**
 * Convertit un [Drawable] en [ImageBitmap] à taille plafonnée.
 *
 * Certaines applications fournissent des bannières en très haute définition :
 * les ramener à [maxWidthPx] garde l'empreinte mémoire sous contrôle sur une
 * box à 2 Go de RAM, sans perte visible à la taille d'affichage.
 * À appeler hors du thread principal.
 */
fun Drawable.toImageBitmap(maxWidthPx: Int): ImageBitmap {
    val sourceWidth = intrinsicWidth.takeIf { it > 0 } ?: maxWidthPx
    val sourceHeight = intrinsicHeight.takeIf { it > 0 } ?: maxWidthPx
    val scale = if (sourceWidth > maxWidthPx) maxWidthPx.toFloat() / sourceWidth else 1f
    val width = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
    val height = (sourceHeight * scale).roundToInt().coerceAtLeast(1)

    val bitmap = toBitmap(width, height, Bitmap.Config.ARGB_8888)
    // Envoie la texture au GPU en avance : le premier affichage ne saccade pas.
    bitmap.prepareToDraw()
    return bitmap.asImageBitmap()
}
