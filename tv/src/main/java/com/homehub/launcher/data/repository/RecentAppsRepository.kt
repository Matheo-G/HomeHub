package com.homehub.launcher.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

/**
 * Historique des applications récemment utilisées.
 *
 * Deux sources sont fusionnées :
 *  1. l'historique interne : chaque lancement depuis HomeHub est horodaté
 *     et persisté (fonctionne partout, sans permission) ;
 *  2. les statistiques d'utilisation système (optionnelles) : elles révèlent
 *     aussi les applications ouvertes autrement (Assistant, touches dédiées de
 *     la télécommande…), si l'utilisateur a accordé l'accès.
 */
class RecentAppsRepository(
    context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    private val appContext: Context = context.applicationContext

    /** Change à chaque retour sur l'accueil pour relire les statistiques système. */
    private val refreshSignal = MutableStateFlow(0L)

    /** Paquets triés du plus récent au plus ancien. */
    val recentPackages: Flow<List<String>> = combine(
        dataStore.data.map { decodeHistory(it[KEY_HISTORY]) }.distinctUntilChanged(),
        refreshSignal,
    ) { history, _ -> history }
        .mapLatest { history -> mergeWithUsageStats(history) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    /** Demande une relecture des statistiques système (appelé à chaque reprise). */
    fun refresh() {
        refreshSignal.value = SystemClock.elapsedRealtime()
    }

    /** Mémorise le lancement d'une application depuis HomeHub. */
    suspend fun recordLaunch(packageName: String) {
        dataStore.edit { preferences ->
            val history = decodeHistory(preferences[KEY_HISTORY]).toMutableMap()
            history[packageName] = System.currentTimeMillis()
            preferences[KEY_HISTORY] = encodeHistory(history)
        }
    }

    /** Vrai si l'utilisateur a autorisé HomeHub à lire l'historique d'utilisation. */
    fun hasUsageAccess(): Boolean {
        val appOps = appContext.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun mergeWithUsageStats(history: Map<String, Long>): List<String> {
        val merged = HashMap(history)
        querySystemUsage().forEach { (packageName, lastUsed) ->
            merged[packageName] = maxOf(merged[packageName] ?: 0L, lastUsed)
        }
        return merged.entries
            .sortedByDescending { it.value }
            .take(MAX_RESULTS)
            .map { it.key }
    }

    /** Dernière utilisation de chaque paquet sur les 7 derniers jours (vide sans permission). */
    private fun querySystemUsage(): Map<String, Long> {
        if (!hasUsageAccess()) return emptyMap()
        val manager = appContext.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val now = System.currentTimeMillis()
        val stats = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - USAGE_WINDOW_MS, now)
        }.getOrNull().orEmpty()

        val result = HashMap<String, Long>()
        stats.forEach { stat ->
            if (stat.lastTimeUsed > 0 && stat.totalTimeInForeground > 0) {
                val previous = result[stat.packageName] ?: 0L
                result[stat.packageName] = maxOf(previous, stat.lastTimeUsed)
            }
        }
        return result
    }

    private fun decodeHistory(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(ENTRY_SEPARATOR).mapNotNull { entry ->
            val separator = entry.lastIndexOf(VALUE_SEPARATOR)
            if (separator <= 0) return@mapNotNull null
            val time = entry.substring(separator + 1).toLongOrNull() ?: return@mapNotNull null
            entry.substring(0, separator) to time
        }.toMap()
    }

    private fun encodeHistory(history: Map<String, Long>): String =
        history.entries
            .sortedByDescending { it.value }
            .take(MAX_HISTORY)
            .joinToString(ENTRY_SEPARATOR) { "${it.key}$VALUE_SEPARATOR${it.value}" }

    private companion object {
        val KEY_HISTORY = stringPreferencesKey("recent_history")
        const val ENTRY_SEPARATOR = ";"
        const val VALUE_SEPARATOR = "="
        const val MAX_HISTORY = 24
        const val MAX_RESULTS = 24
        const val USAGE_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    }
}
