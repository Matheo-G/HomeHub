package com.homehub.launcher.data.source

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Fichier de préférences unique de HomeHub (réglages + historique des lancements).
 *
 * Le délégué `preferencesDataStore` garantit une seule instance par processus,
 * condition indispensable au bon fonctionnement de DataStore.
 */
val Context.homeHubDataStore: DataStore<Preferences> by preferencesDataStore(name = "homehub")
