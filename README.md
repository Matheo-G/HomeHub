# HomeHub

[![Android CI](https://github.com/Matheo-G/HomeHub/actions/workflows/android.yml/badge.svg)](https://github.com/Matheo-G/HomeHub/actions/workflows/android.yml)

Un launcher Android TV moderne, sobre et fluide, écrit en Kotlin avec Jetpack Compose for TV.

HomeHub remplace l'écran d'accueil d'origine par une interface pensée pour la télécommande : de grandes cartes, un focus lumineux et vivant, un fond animé discret, et rien qui ne détourne l'attention du contenu.

> **V1** — base fonctionnelle, architecture prête pour la suite (widgets, intégrations, thèmes).

---

## Fonctionnalités

- **Vrai launcher** : déclaré `HOME` + `DEFAULT` (candidat écran d'accueil) et `LAUNCHER` + `LEANBACK_LAUNCHER` (présent dans la liste des applications TV). Le bouton Home ramène toujours à l'accueil.
- **Barre d'état discrète** : salutation (« Bonsoir, Mathéo »), date, heure, état réseau (Wi-Fi avec niveau, Ethernet, hors ligne).
- **Favoris** : Netflix, YouTube, Deezer, Plex, Prime Video, Paramètres, Toutes les applications. Les cartes affichent la bannière TV officielle de chaque application installée ; une application absente propose « Installer », qui ouvre le Play Store.
- **Applications récentes** : rangée horizontale mise à jour à chaque retour sur l'accueil.
- **Toutes les applications** : grille alphabétique, actualisée en direct à chaque installation ou désinstallation.
- **Focus premium** : agrandissement sur ressort, soulèvement, ombre profonde, halo et contour de la couleur d'accent, reflet diagonal.
- **Fond animé** : dégradé sombre, deux lumières qui dérivent, particules qui montent en scintillant. Boucle de 2 minutes parfaitement raccordée.
- **Personnalisation** (enregistrée localement) : 6 couleurs d'accent, 3 fonds (Aurore, Nébuleuse, Minuit statique), 3 tailles de cartes. Tout s'applique en direct, avec transitions.

## Compatibilité

| | |
|---|---|
| Android | 8.0 et plus (`minSdk 26`), donc toute box Android 9+ |
| Appareil de référence | Xiaomi Mi Box MDZ-16-AB |
| Autres | Toute box ou TV Android TV / Google TV |
| Navigation | Télécommande uniquement (croix directionnelle, OK, Retour, Home) |

## Stack technique

- Kotlin 2.0, Jetpack Compose (BOM 2024.12), **Compose for TV** (`androidx.tv:tv-material` 1.0, Material 3)
- Architecture **MVVM** + flux réactifs (`StateFlow`)
- **DataStore Preferences** pour les réglages et l'historique
- Gradle Kotlin DSL + catalogue de versions (`gradle/libs.versions.toml`)
- **GitHub Actions** : APK release générée à chaque push sur `main`
- Aucune dépendance superflue : pas de framework d'injection, pas de bibliothèque d'icônes, pas de navigation-compose

## Architecture

```
app/src/main/java/com/homehub/launcher/
├── HomeHubApplication.kt        Crée le conteneur de dépendances
├── MainActivity.kt              Activité unique (catégorie HOME)
├── di/
│   └── AppContainer.kt          Injection manuelle, singletons paresseux
├── data/
│   ├── models/                  AppInfo, Artwork, HomeTile, HomeModule, LauncherSettings, ConnectionStatus
│   ├── source/                  FavoritesCatalog (paquets TV + mobile), DataStore
│   └── repository/
│       ├── AppsRepository.kt          Apps installées (rechargées sur PACKAGE_*) + visuels en cache
│       ├── RecentAppsRepository.kt    Historique interne + UsageStats (optionnel)
│       ├── SettingsRepository.kt      Réglages (DataStore)
│       └── ConnectivityRepository.kt  État réseau (callback système)
├── viewmodel/
│   ├── HomeViewModel.kt         Favoris, récents, toutes les apps, réseau
│   └── SettingsViewModel.kt     Réglages (thème global + page Paramètres)
├── utils/                       AppLauncher, Clock, conversion d'images
└── ui/
    ├── HomeHubApp.kt            Racine : thème + fond + navigation
    ├── theme/                   Couleurs, typographie, dimensions, mouvement
    ├── components/              FocusableSurface, cartes, fond animé, barre d'état, glyphes
    ├── screens/                 HomeScreen, AllAppsScreen, SettingsScreen
    └── navigation/              Destinations, pile de navigation, transitions
```

### Choix techniques clés

- **Zéro recomposition pendant les animations.** Focus, fond et fondus lisent leurs valeurs animées uniquement dans les phases de dessin (`graphicsLayer`, `drawBehind`, `drawWithCache`). Une animation redessine un calque, elle ne recompose jamais l'arbre.
- **Horloge isolée.** La barre d'état collecte son propre flux, qui ne se réveille qu'au changement de minute.
- **Images pré-décodées.** Bannières et icônes sont converties en `ImageBitmap` sur un thread d'E/S, à taille plafonnée, dans un cache LRU de 24 Mo (box à 2 Go de RAM).
- **Halo sans flou GPU.** Superposition de contours translucides : rendu doux, coût quasi nul, compatible Android 8 (`RenderEffect` exige Android 12).
- **Rien ne tourne en arrière-plan.** L'horloge d'animation Compose est suspendue quand le launcher n'est plus visible ; les flux s'arrêtent 5 s après la sortie de l'écran.

## Télécharger l'APK (GitHub Actions)

Chaque push sur `main` compile automatiquement l'APK :

1. Ouvrez l'onglet **Actions** du dépôt, puis le dernier run **Android CI** marqué ✓.
2. En bas de la page, section **Artifacts**, téléchargez **HomeHub-release** (un `.zip`).
3. Décompressez-le : vous obtenez `HomeHub-v1.0.0-buildN.apk`.

Toutes les APK sont signées avec la même clé de développement (`app/debug.keystore`) : une nouvelle version s'installe **par-dessus** la précédente, sans désinstallation.

### APK signée avec votre propre clé (optionnel)

Ajoutez ces secrets dans *Settings → Secrets and variables → Actions* ; le workflow produira alors aussi l'artefact **HomeHub-release-signed** :

| Secret | Contenu |
|---|---|
| `HOMEHUB_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `HOMEHUB_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `HOMEHUB_KEY_ALIAS` | alias de la clé |
| `HOMEHUB_KEY_PASSWORD` | mot de passe de la clé |

> Une APK signée par une autre clé ne peut pas mettre à jour une installation existante : désinstallez d'abord la version précédente.

## Compiler en local

Prérequis : Android Studio (dernière version stable), qui fournit le JDK 17 et le SDK Android 35.

1. Ouvrez le dossier dans Android Studio et laissez la synchronisation Gradle se terminer.
2. Compilez l'APK release (nettement plus fluide que la version debug sur une box) :

```bash
./gradlew assembleRelease        # Windows : gradlew.bat assembleRelease
```

L'APK est générée dans `app/build/outputs/apk/release/app-release.apk`.

## Installer sur la Mi Box avec « Send Files to TV »

1. **Installez Send Files to TV** sur la Mi Box (Play Store de la box) et sur votre téléphone ou PC, sur le même réseau.
2. **Autorisez l'installation** : sur la box, *Paramètres → Préférences de l'appareil → Sécurité et restrictions → Sources inconnues*, puis activez **Send Files to TV**.
3. Sur la box, ouvrez Send Files to TV et choisissez **Recevoir**.
4. Sur le téléphone, choisissez **Envoyer**, sélectionnez l'APK, puis votre Mi Box.
5. Sur la box, ouvrez le fichier reçu (dossier *Download*) et validez **Installer**.

Si l'APK ne s'ouvre pas depuis l'application, passez par un gestionnaire de fichiers TV (par exemple *X-plore File Manager*).

### Alternative : ADB en Wi-Fi

```bash
adb connect <IP_DE_LA_BOX>:5555
adb install -r HomeHub-v1.0.0-buildN.apk
```

## Faire de HomeHub l'écran d'accueil

Après installation, HomeHub apparaît dans la liste des applications. Sur beaucoup de box Android TV, le bouton Home reste associé au launcher d'origine. Deux options :

- **Sans modification système** : une application de remappage (ex. *Button Mapper*) ouvre HomeHub sur un appui du bouton Home.
- **Avec ADB (réversible)** : désactivez le launcher d'origine ; Android utilise alors HomeHub.

```bash
# Mi Box en Android 8 (launcher « Leanback »)
adb shell pm disable-user --user 0 com.google.android.leanbacklauncher

# Box en Android 9 et plus
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

Retour en arrière à tout moment :

```bash
adb shell pm enable com.google.android.leanbacklauncher   # ou com.google.android.tvlauncher
```

> Testez d'abord HomeHub depuis la liste des applications avant de désactiver le launcher d'origine.

## Applications récentes : historique système (optionnel)

Par défaut, HomeHub mémorise les applications ouvertes depuis l'accueil. Pour voir aussi celles lancées autrement (Assistant, touches Netflix/YouTube de la télécommande…) : *Paramètres de HomeHub → Système → Historique d'utilisation*. Si cet écran n'existe pas sur votre box :

```bash
adb shell appops set com.homehub.launcher GET_USAGE_STATS allow
```

## Étendre HomeHub

L'accueil est une liste de **modules** (`data/models/HomeModule.kt`). Pour ajouter un widget météo, un agenda, un lecteur Deezer/Spotify, un accès NAS ou un tableau de bord domotique :

1. Ajoutez un sous-type à `HomeModule`.
2. Créez son repository dans `data/repository/`, déclarez-le dans `AppContainer` et exposez son état depuis un ViewModel.
3. Ajoutez son rendu dans le `when` de `HomeScreen` : le compilateur signale tout module oublié.

De même : un nouveau type de tuile dans `HomeTile`, une nouvelle cible de lancement (Steam Link, scène domotique…) dans `AppLauncher`, un nouveau fond dans `WallpaperStyle` + `wallpaperPalette()`.

## Feuille de route

- [ ] Favoris éditables (ajout, retrait, réordonnancement)
- [ ] Widgets : météo, agenda Google, lecture en cours (Deezer, Spotify)
- [ ] Raccourcis NAS, Steam Link, tableau de bord personnel
- [ ] Maison connectée
- [ ] Assistant IA
- [ ] Thèmes complets et synchronisation cloud des réglages
- [ ] Baseline profile dédié et tests UI

## Licence

Code sous licence MIT — voir [LICENSE](LICENSE).

HomeHub n'embarque aucun logo tiers : les visuels affichés sont fournis par les applications installées elles-mêmes.
