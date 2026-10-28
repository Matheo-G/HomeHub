package com.homehub.launcher.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.homehub.launcher.data.models.AccentColor
import com.homehub.launcher.data.models.CardSize
import com.homehub.launcher.data.models.LauncherSettings
import com.homehub.launcher.data.models.WallpaperStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

/**
 * Réglages persistants de HomeHub (DataStore Preferences).
 *
 * Les énumérations sont enregistrées par leur nom : renommer une constante
 * fait simplement retomber sur la valeur par défaut, sans plantage.
 * La lecture démarre dès la création (partage « Eagerly ») pour que le thème
 * soit prêt avant le premier affichage.
 *
 * Point d'extension naturel pour la synchronisation cloud : il suffira
 * d'observer [settings] et de pousser les changements vers un service distant.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    val settings: StateFlow<LauncherSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toSettings() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, LauncherSettings())

    suspend fun setAccent(accent: AccentColor) {
        dataStore.edit { it[KEY_ACCENT] = accent.name }
    }

    suspend fun setWallpaper(wallpaper: WallpaperStyle) {
        dataStore.edit { it[KEY_WALLPAPER] = wallpaper.name }
    }

    suspend fun setCardSize(cardSize: CardSize) {
        dataStore.edit { it[KEY_CARD_SIZE] = cardSize.name }
    }

    private fun Preferences.toSettings(): LauncherSettings = LauncherSettings(
        accent = enumOrDefault(this[KEY_ACCENT], AccentColor.ELECTRIC_BLUE),
        wallpaper = enumOrDefault(this[KEY_WALLPAPER], WallpaperStyle.AURORA),
        cardSize = enumOrDefault(this[KEY_CARD_SIZE], CardSize.STANDARD),
        userName = this[KEY_USER_NAME]?.takeIf { it.isNotBlank() } ?: LauncherSettings.DEFAULT_USER_NAME,
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        val KEY_ACCENT = stringPreferencesKey("accent")
        val KEY_WALLPAPER = stringPreferencesKey("wallpaper")
        val KEY_CARD_SIZE = stringPreferencesKey("card_size")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
    }
}
