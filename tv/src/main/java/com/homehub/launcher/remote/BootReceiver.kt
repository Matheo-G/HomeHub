package com.homehub.launcher.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Relance la télécommande au démarrage de la box (et après une mise à jour
 * de HomeHub). Le service vérifie lui-même que l'option est activée.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> RemoteControlService.sync(context, enabled = true)
        }
    }
}
