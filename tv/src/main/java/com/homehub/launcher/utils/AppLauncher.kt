package com.homehub.launcher.utils

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import com.homehub.launcher.MainActivity

/**
 * Point unique de lancement des cibles externes.
 *
 * Toutes les méthodes renvoient `true` si une activité a bien été ouverte,
 * sans jamais lever d'exception : un launcher ne doit pas planter parce
 * qu'une application a été désinstallée entre-temps.
 *
 * Les futures cibles (Steam Link, partage NAS, scène domotique…) s'ajoutent
 * ici sous forme de nouvelles méthodes.
 */
class AppLauncher(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Ouvre une application, en privilégiant son entrée Android TV. */
    fun launchApp(packageName: String): Boolean {
        val packageManager = appContext.packageManager
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        return start(intent)
    }

    /** Ouvre la fiche Play Store d'une application, pour l'installer. */
    fun openStorePage(packageName: String): Boolean =
        start(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))) ||
            start(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))

    /** Ouvre les paramètres système de l'appareil. */
    fun openSystemSettings(): Boolean = start(Intent(Settings.ACTION_SETTINGS))

    /** Ouvre l'écran système d'accès à l'historique d'utilisation, s'il existe. */
    fun openUsageAccessSettings(): Boolean =
        start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    /** Ouvre l'écran système d'accessibilité (activation du contrôle complet). */
    fun openAccessibilitySettings(): Boolean =
        start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) || openSystemSettings()

    /**
     * Ramène HomeHub au premier plan, éventuellement sur un écran précis
     * ([MainActivity.EXTRA_DESTINATION]).
     */
    fun openHomeHub(destination: String? = null): Boolean {
        val intent = Intent(appContext, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
        destination?.let { intent.putExtra(MainActivity.EXTRA_DESTINATION, it) }
        return start(intent)
    }

    /**
     * Recherche dans une application précise. Essaie l'intention standard de
     * recherche, puis le lien web de recherche YouTube que les applications
     * YouTube savent ouvrir.
     */
    fun searchInApp(packageName: String, query: String): Boolean {
        val search = Intent(Intent.ACTION_SEARCH)
            .setPackage(packageName)
            .putExtra(SearchManager.QUERY, query)
        if (start(search)) return true
        if (packageName.contains("youtube")) {
            val url = "https://www.youtube.com/results?search_query=" + Uri.encode(query)
            if (start(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(packageName))) return true
        }
        return false
    }

    /**
     * Lance la lecture d'un contenu par recherche (« joue Daft Punk sur Deezer »).
     * Intention gérée par la plupart des applications musicales et vidéo.
     */
    fun playFromSearch(packageName: String?, query: String): Boolean {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        packageName?.let(intent::setPackage)
        return start(intent)
    }

    /** Ouvre un lien (deep link), éventuellement dans une application précise. */
    fun openUri(packageName: String?, uri: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        packageName?.let(intent::setPackage)
        return start(intent)
    }

    /** Recherche globale Android TV (Assistant / recherche système). */
    fun globalSearch(query: String): Boolean =
        start(Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH).putExtra(SearchManager.QUERY, query)) ||
            start(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query))

    private fun start(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        appContext.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
