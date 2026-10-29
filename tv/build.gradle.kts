import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Module applicatif HomeHub.
 *
 * Signature :
 *  - par défaut, la release est signée avec la clé de développement du dépôt
 *    (signing/dev.keystore, partagée avec HomeHub Remote). Toutes les APK produites (poste local comme CI)
 *    portent donc la même signature et se mettent à jour par-dessus
 *    l'installation précédente sans désinstallation.
 *  - si les variables d'environnement HOMEHUB_KEYSTORE_* sont présentes
 *    (GitHub Secrets en CI), la release est signée avec cette clé privée.
 */
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/** Keystore de publication fourni par l'environnement, s'il existe. */
val releaseKeystore: File? = System.getenv("HOMEHUB_KEYSTORE_FILE")
    ?.takeIf { it.isNotBlank() }
    ?.let { file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.homehub.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.homehub.launcher"
        // Android 8.0 minimum : couvre la Mi Box MDZ-16-AB (8.1) et toute box Android 9+.
        minSdk = 26
        targetSdk = 35
        // En CI, le numéro de build GitHub garantit un versionCode croissant.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = providers.gradleProperty("homehub.version").get()

        // Lien stable vers la dernière télécommande publiée (QR code de l'écran d'appairage).
        buildConfigField(
            "String",
            "REMOTE_APK_URL",
            "\"https://github.com/Matheo-G/HomeHub/releases/latest/download/HomeHub-Remote.apk\"",
        )
    }

    signingConfigs {
        // Clé de développement partagée (non secrète, réservée aux tests).
        getByName("debug") {
            storeFile = rootProject.file("signing/dev.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("HOMEHUB_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HOMEHUB_KEY_ALIAS")
                keyPassword = System.getenv("HOMEHUB_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Le lint complet tourne à part ; il ne doit pas bloquer la production d'APK.
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.profileinstaller)

    implementation(project(":core:protocol"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.java.websocket)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
