package com.homehub.launcher.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.homehub.launcher.data.models.AppInfo
import com.homehub.launcher.data.models.Artwork
import com.homehub.launcher.utils.toImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.Collator
import java.util.Locale

/**
 * Source de vérité des applications installées et de leurs visuels.
 *
 * - La liste est rechargée automatiquement à chaque installation, mise à jour
 *   ou désinstallation (diffusions système `PACKAGE_*`).
 * - Le récepteur n'est enregistré que tant que quelqu'un observe la liste :
 *   aucune activité en arrière-plan quand le launcher n'est pas affiché.
 * - Les visuels sont décodés sur un thread d'E/S et gardés dans un cache LRU
 *   borné en octets.
 */
class AppsRepository(
    context: Context,
    scope: CoroutineScope,
) {
    private val appContext: Context = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val ownPackage: String = appContext.packageName

    /** Cache des visuels, dimensionné en octets (environ 24 Mo). */
    private val artworkCache = object : LruCache<String, Artwork>(ARTWORK_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Artwork): Int =
            value.bitmap.asAndroidBitmap().allocationByteCount
    }

    /** Émet une valeur au démarrage puis à chaque changement de paquet. */
    private val packageChanges: Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Un paquet mis à jour peut avoir changé de bannière.
                intent?.data?.schemeSpecificPart?.let { artworkCache.remove(it) }
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        trySend(Unit)
        awaitClose { appContext.unregisterReceiver(receiver) }
    }.conflate()

    /**
     * Applications lançables, triées alphabétiquement.
     * `null` tant que la première lecture n'est pas terminée (évite d'afficher
     * brièvement des favoris « non installés »).
     */
    val installedApps: StateFlow<List<AppInfo>?> = packageChanges
        .mapLatest { queryLaunchableApps() }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Lecture directe de la liste (télécommande, quand l'accueil n'est pas affiché). */
    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) { queryLaunchableApps() }

    /**
     * Icône carrée encodée en PNG pour la télécommande (128 px, quelques Ko).
     * `null` si l'application n'existe plus.
     */
    suspend fun loadIconPng(packageName: String): ByteArray? = withContext(Dispatchers.IO) {
        val icon = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
            ?: return@withContext null
        val bitmap = icon.toBitmap(REMOTE_ICON_PX, REMOTE_ICON_PX, Bitmap.Config.ARGB_8888)
        ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    }

    /** Retourne le visuel déjà en cache, sans aucun travail. */
    fun peekArtwork(packageName: String): Artwork? = artworkCache.get(packageName)

    /** Charge (ou relit depuis le cache) le visuel d'une application. */
    suspend fun loadArtwork(packageName: String): Artwork? = withContext(Dispatchers.IO) {
        artworkCache.get(packageName)
            ?: decodeArtwork(packageName)?.also { artworkCache.put(packageName, it) }
    }

    /**
     * Préfère la bannière TV (activité Leanback puis application), sinon l'icône.
     */
    private fun decodeArtwork(packageName: String): Artwork? {
        val banner = runCatching {
            packageManager.getLeanbackLaunchIntentForPackage(packageName)
                ?.let { packageManager.getActivityBanner(it) }
                ?: packageManager.getApplicationBanner(packageName)
        }.getOrNull()
        if (banner != null) {
            return Artwork(banner.toImageBitmap(MAX_BANNER_WIDTH_PX), isBanner = true)
        }
        val icon = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
            ?: return null
        return Artwork(icon.toImageBitmap(MAX_ICON_WIDTH_PX), isBanner = false)
    }

    /** Fusionne les activités TV et classiques ; une entrée par paquet. */
    private fun queryLaunchableApps(): List<AppInfo> {
        val byPackage = LinkedHashMap<String, AppInfo>()
        queryActivities(Intent.CATEGORY_LEANBACK_LAUNCHER).forEach { info ->
            addApp(byPackage, info, isTvApp = true)
        }
        queryActivities(Intent.CATEGORY_LAUNCHER).forEach { info ->
            addApp(byPackage, info, isTvApp = false)
        }
        val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }
        return byPackage.values.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun addApp(target: MutableMap<String, AppInfo>, info: ResolveInfo, isTvApp: Boolean) {
        val packageName = info.activityInfo?.packageName ?: return
        if (packageName == ownPackage || packageName in target) return
        val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
        target[packageName] = AppInfo(
            packageName = packageName,
            label = label.ifEmpty { packageName },
            isTvApp = isTvApp,
        )
    }

    private fun queryActivities(category: String): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val ARTWORK_CACHE_BYTES = 24 * 1024 * 1024
        const val MAX_BANNER_WIDTH_PX = 480
        const val MAX_ICON_WIDTH_PX = 192
        const val REMOTE_ICON_PX = 128
    }
}
