package com.homehub.launcher

import android.app.Application
import com.homehub.launcher.di.AppContainer

/**
 * Point d'entrée du processus : crée le conteneur de dépendances et
 * démarre la lecture des réglages pour que le thème soit prêt au premier écran.
 */
class HomeHubApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Accès anticipé : lance la lecture DataStore pendant que l'activité se crée.
        container.settingsRepository
    }
}
