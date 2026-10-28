package com.homehub.launcher.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

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
