package com.homehub.remote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.remoteDataStore: DataStore<Preferences> by preferencesDataStore(name = "remote")

/**
 * Données locales de la télécommande :
 *  - identifiant aléatoire du téléphone (aucun identifiant matériel) ;
 *  - jeton d'appairage par box ;
 *  - dernière box utilisée, pour la reconnexion instantanée.
 */
class PhoneStore(context: Context) {

    private val dataStore = context.applicationContext.remoteDataStore

    suspend fun deviceId(): String {
        dataStore.data.first()[KEY_DEVICE_ID]?.let { return it }
        var id = ""
        dataStore.edit { prefs ->
            id = prefs[KEY_DEVICE_ID] ?: UUID.randomUUID().toString().also { prefs[KEY_DEVICE_ID] = it }
        }
        return id
    }

    suspend fun token(boxId: String): String? = dataStore.data.first()[tokenKey(boxId)]

    suspend fun saveToken(boxId: String, token: String) {
        dataStore.edit { it[tokenKey(boxId)] = token }
    }

    suspend fun clearToken(boxId: String) {
        dataStore.edit { it.remove(tokenKey(boxId)) }
    }

    suspend fun lastBox(): BoxAddress? {
        val prefs = dataStore.data.first()
        val host = prefs[KEY_LAST_HOST] ?: return null
        val port = prefs[KEY_LAST_PORT] ?: return null
        return BoxAddress(
            host = host,
            port = port,
            name = prefs[KEY_LAST_NAME] ?: host,
            boxId = prefs[KEY_LAST_ID],
        )
    }

    suspend fun saveLastBox(box: BoxAddress) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_HOST] = box.host
            prefs[KEY_LAST_PORT] = box.port
            prefs[KEY_LAST_NAME] = box.name
            box.boxId?.let { prefs[KEY_LAST_ID] = it } ?: prefs.remove(KEY_LAST_ID)
        }
    }

    suspend fun forgetLastBox() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_LAST_HOST)
            prefs.remove(KEY_LAST_PORT)
            prefs.remove(KEY_LAST_NAME)
            prefs.remove(KEY_LAST_ID)
        }
    }

    private fun tokenKey(boxId: String) = stringPreferencesKey("token_$boxId")

    private companion object {
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_LAST_HOST = stringPreferencesKey("last_host")
        val KEY_LAST_PORT = intPreferencesKey("last_port")
        val KEY_LAST_NAME = stringPreferencesKey("last_name")
        val KEY_LAST_ID = stringPreferencesKey("last_id")
    }
}
