import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * :core:protocol — protocole local HomeHub v2 (JSON-RPC 2.0 sur WebSocket).
 *
 * Kotlin pur, sans Android : le même code (méthodes, paramètres, erreurs)
 * est compilé dans la TV et dans le téléphone, et testé sur JVM.
 * Spécification : docs/api/local-api.md.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    // Messages d'assertion complets dans le journal de CI (annotations d'erreur lisibles).
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}
