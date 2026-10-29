package com.homehub.launcher.data.models

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Visuel d'une application, déjà décodé et prêt à être dessiné.
 *
 * @property bitmap image pré-décodée (jamais de décodage sur le thread UI).
 * @property isBanner vrai pour une bannière TV 16:9, faux pour une icône carrée.
 */
@Immutable
class Artwork(
    val bitmap: ImageBitmap,
    val isBanner: Boolean,
)
