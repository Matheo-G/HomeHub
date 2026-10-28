package com.homehub.launcher.data.models

import androidx.compose.runtime.Immutable

/**
 * Application lançable installée sur l'appareil.
 *
 * Volontairement réduite à des valeurs simples et immuables : aucun objet
 * Android (Intent, Drawable) n'y transite, ce qui la rend stable pour Compose
 * (pas de recomposition inutile) et sûre à partager entre threads.
 * Les visuels sont chargés à la demande par [com.homehub.launcher.data.repository.AppsRepository].
 *
 * @property packageName identifiant unique du paquet.
 * @property label nom affiché à l'utilisateur.
 * @property isTvApp vrai si l'application déclare une entrée Android TV (Leanback),
 *                   ce qui garantit en général une bannière 16:9.
 */
@Immutable
data class AppInfo(
    val packageName: String,
    val label: String,
    val isTvApp: Boolean,
)
