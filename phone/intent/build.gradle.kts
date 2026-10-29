import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * :phone:intent — moteur d'intentions de HomeHub Remote (docs/adr/0006).
 *
 * Kotlin pur : normalisation, règles françaises, intents typés.
 * Testé sur JVM, sans émulateur ; la TV n'en dépend jamais (docs/adr/0002).
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
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
    api(project(":core:protocol"))
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
