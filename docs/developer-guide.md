# Guide développeur

## 1. Prérequis

| Outil | Version |
|---|---|
| Android Studio | Dernière version stable |
| JDK | 17 (fourni par Android Studio) |
| SDK Android | Plateforme 35, build-tools 35.0.0 |
| Appareil TV | Box Android TV 8+ ou émulateur « Television (1080p) » API 28 |
| Téléphone | Android 8+ (ou émulateur), même réseau que la TV |

## 2. Récupérer et compiler

```bash
git clone https://github.com/Matheo-G/HomeHub.git
cd HomeHub
./gradlew :tv:assembleDebug :phone:assembleDebug        # APK de développement
./gradlew :tv:assembleRelease :phone:assembleRelease    # APK optimisées (R8)
./gradlew :core:protocol:test :phone:intent:test :phone:sources:test :tv:testReleaseUnitTest   # tests
```

Sorties :
- TV : `tv/build/outputs/apk/release/tv-release.apk`
- Téléphone : `phone/build/outputs/apk/release/phone-release.apk`

La version des deux applications est définie à un seul endroit : `homehub.version` dans `gradle.properties`.

Les versions `debug` ont un suffixe d'identifiant (`.debug`) : elles s'installent à côté des versions publiées.

## 3. Organisation du dépôt

```
HomeHub/
├── tv/             :tv — HomeHub TV (launcher, couche de commandes, serveur WebSocket)
├── phone/          :phone — HomeHub Remote (le cerveau : session, voix, planificateur)
│   ├── intent/     :phone:intent — moteur d'intentions (Kotlin pur) + corpus de phrases
│   └── sources/    :phone:sources — recherche universelle : TMDB, applications, agrégateur (Kotlin pur)
├── core/protocol/  :core:protocol — API locale v2 (Kotlin pur) + fixtures de contrat
├── signing/        Clé de développement partagée (dev.keystore)
├── docs/           Vision, architecture, API, roadmap, ADR, changelog
├── .github/        CI, modèles d'issues et de pull requests
├── gradle/         Catalogue de versions (libs.versions.toml), wrapper
├── CONTRIBUTING.md
└── SECURITY.md
```

Architecture détaillée : [architecture.md](architecture.md).

## 4. Installer sur les appareils

### TV (Mi Box)
- **Send Files to TV** : envoyer l'APK depuis le téléphone, l'ouvrir dans le dossier *Download*.
- **ADB** (si disponible) : `adb connect <ip>:5555 && adb install -r app-release.apk`.

### Téléphone
- Ouvrir le lien de la release, ou `adb install -r remote-release.apk`.

## 5. Workflow de contribution

Résumé de [ADR-0010](adr/0010-workflow-git-et-versionnage.md) :

1. Partir de `main` à jour : `git switch -c feature/v3-mon-sujet`.
2. Commits au format Conventional Commits : `feat(tv): …`, `fix(phone): …`, `docs(api): …`.
3. Pousser et ouvrir une pull request (modèle fourni) : la CI compile, teste et publie les APK en artefacts.
4. Tester les artefacts sur la Mi Box si l'interface ou le réseau changent.
5. Fusion (squash) uniquement quand la CI est verte.

Une pull request qui touche le protocole met à jour [l'API locale](api/local-api.md) ; une décision structurante ajoute une [ADR](adr/README.md).

## 6. Conventions de code

- Kotlin officiel (`kotlin.code.style=official`), 120 colonnes, virgules finales ; ktlint + detekt en CI à partir de la V3.
- Commentaires et textes d'interface en français ; identifiants en anglais.
- Compose : état hissé dans les ViewModels, valeurs animées lues en phase de dessin (`graphicsLayer`, `drawBehind`), types `@Immutable`/`@Stable` pour les modèles passés aux composables.
- Coroutines : `viewModelScope` dans les ViewModels, portée applicative injectée ailleurs ; jamais de `GlobalScope` ; E/S sur `Dispatchers.IO`.
- Aucune exception ne doit remonter jusqu'au launcher : les appels système renvoient un résultat (`Boolean`, `Result`).
- Pas de nouvelle dépendance sans justification dans la pull request (et ADR si majeure).

## 7. Tests

| Niveau | Où | Exemple |
|---|---|---|
| Unitaires JVM | `src/test` | Grammaire vocale (`VoiceCommandInterpreterTest`) |
| Contrat de protocole | `core/protocol/src/test` | Chaque exemple JSON de l'API (`fixtures/`) est décodé puis réécrit sans perte par les types partagés |
| Compréhension | `phone/intent/src/test` | Corpus de phrases (`corpus.tsv`) : taux de compréhension mesuré à chaque build |
| Interface | `src/androidTest` (V5) | Navigation au pavé directionnel sur l'accueil |
| Manuel | Mi Box | Liste de vérification dans le modèle de pull request |

## 8. Étendre HomeHub

### Ajouter une commande TV
1. Documenter la méthode dans [l'API locale](api/local-api.md) (+ capacité si nouvelle).
2. Déclarer paramètres et résultat dans `core/protocol/.../Messages.kt`, la méthode dans `Methods.kt`, et une fixture JSON (le test de contrat échoue sinon).
3. Ajouter le cas `TvCommand`, sa branche dans `CommandDispatcher` et le handler du domaine (`tv/.../command/`), puis la route dans `RpcRouter`.
4. L'appeler depuis le planificateur du téléphone (`phone/.../planner/IntentExecutor.kt`).

### Ajouter une intention
1. Ajouter des phrases au corpus (`phone/intent/src/test/resources/corpus.tsv`).
2. Ajouter la règle dans `IntentParser` (ou un cas de `UserIntent` s'il est nouveau).
3. Traduire l'intention en commandes dans `IntentExecutor`.

### Ajouter un fournisseur d'IA
Implémenter `AIProvider`, l'enregistrer dans `AIProviderRegistry`, déclarer ses champs de configuration ([ADR-0005](adr/0005-abstraction-aiprovider.md)).

### Ajouter une source de recherche
Implémenter `SearchSource` dans `phone/sources` (test avec `MockWebServer`), puis l'ajouter à la liste de `SearchAggregator` dans `RemoteContainer` ; une clé éventuelle va dans `SecretStore` ([ADR-0007](adr/0007-recherche-universelle.md)).

### Ajouter un type de carte
ADR courte, ajout du `kind` dans le protocole, composant de rendu TV, repli générique vérifié.

## 9. Déboguer

- **Journaux** : `adb logcat -s HomeHub`.
- **Protocole** : `websocat ws://<ip-tv>:8765/homehub`, voir [l'API locale](api/local-api.md#8-tester-à-la-main).
- **Découverte** : `avahi-browse -r _homehub._tcp` (Linux) ou `dns-sd -B _homehub._tcp` (macOS).
- **Performances** : Android Studio Profiler, `adb shell dumpsys gfxinfo com.homehub.launcher`.

## 10. Publier

Automatique : chaque fusion dans `main` met à jour la release « Dernière version » (APK `HomeHub-TV.apk` et `HomeHub-Remote.apk`). Une version numérotée = un tag `vX.Y.Z` sur `main` + entrée de [changelog](changelog.md).

Signature : clé de développement du dépôt par défaut ; clé de publication via les secrets `HOMEHUB_KEYSTORE_*` (voir le README).
