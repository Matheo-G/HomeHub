package com.homehub.remote.secrets

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coffre des secrets de HomeHub Remote (clés d'API des sources et, en V4,
 * des fournisseurs d'IA) — docs/adr/0004.
 *
 * Chiffrement AES-256 avec une clé maîtresse de l'Android Keystore : les
 * valeurs ne sont jamais lisibles hors de l'application, jamais journalisées
 * et jamais transmises à la TV. Exclu des sauvegardes Android.
 */
class SecretStore(context: Context) {

    private val preferences: SharedPreferences? = runCatching {
        // Clé maîtresse AES-256-GCM créée (une fois) dans l'Android Keystore.
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            FILE_NAME,
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    private val tmdbState = MutableStateFlow(get(TMDB_KEY))

    /** Clé TMDB (ou jeton de lecture v4), `null` si non configurée. */
    val tmdbKey: StateFlow<String?> = tmdbState.asStateFlow()

    /** Faux si le Keystore est indisponible (appareil défectueux) : rien n'est alors enregistré. */
    val isAvailable: Boolean get() = preferences != null

    fun setTmdbKey(value: String?) {
        val clean = value?.trim()?.takeIf { it.isNotEmpty() }
        put(TMDB_KEY, clean)
        tmdbState.value = clean
    }

    private fun get(key: String): String? = runCatching { preferences?.getString(key, null) }.getOrNull()

    private fun put(key: String, value: String?) {
        preferences?.edit()?.apply {
            if (value == null) remove(key) else putString(key, value)
        }?.apply()
    }

    private companion object {
        const val FILE_NAME = "homehub_secrets"
        const val TMDB_KEY = "tmdb"
    }
}
