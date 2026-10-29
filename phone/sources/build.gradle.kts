import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * :phone:sources — sources de la recherche universelle (docs/adr/0007).
 *
 * Kotlin pur : TMDB (films, séries, personnes, disponibilité par service),
 * applications installées, agrégation parallèle. Exécuté sur le téléphone
 * uniquement : la TV n'appelle jamais Internet (docs/adr/0002).
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
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}
