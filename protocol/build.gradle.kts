/*
 * Protocole HomeHub : messages échangés entre le launcher TV (:app) et la
 * télécommande téléphone (:remote).
 *
 * Bibliothèque volontairement minimale (aucune dépendance hors SDK Android) :
 * les deux applications partagent exactement le même format, et toute
 * évolution du protocole est vérifiée par le compilateur des deux côtés.
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.homehub.protocol"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
