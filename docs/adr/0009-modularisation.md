# ADR-0009 — Modularisation Gradle et règles de dépendance

- **Statut** : Acceptée
- **Date** : 2026-10-29

## Contexte

Le dépôt contient `:app` (TV), `:remote` (téléphone) et `:protocol` (bibliothèque Android utilisant `org.json`). Les noms ne reflètent pas l'écosystème, le protocole ne peut pas être testé sur JVM sans Android, et l'arrivée des intentions, de l'IA et des sources demande des frontières nettes.

## Décision

Structure cible, mise en place au lot 3.1 :

| Module | Type | Rôle |
|---|---|---|
| `:core:protocol` | Kotlin/JVM | Messages JSON-RPC, méthodes, cartes, codec (kotlinx.serialization) |
| `:core:testing` | Kotlin/JVM | Fixtures de contrat (exemples JSON de l'API), utilitaires de test |
| `:tv` | Application Android TV | Launcher, rendu, commandes, serveur (`applicationId` inchangé : `com.homehub.launcher`) |
| `:phone` | Application Android | Interface, session, voix (`applicationId` inchangé : `com.homehub.remote`) |
| `:phone:intent` | Kotlin/JVM | Normalisation, règles, intents, planificateur |
| `:phone:ai` | Bibliothèque Android | `AIProvider`, registre, fournisseurs |
| `:phone:sources` | Bibliothèque Android | `SearchSource`, météo, Plex… |

Règles vérifiées en revue (et par un test de graphe de dépendances en CI) :
- `:tv` ne dépend d'aucun module `:phone:*` ;
- `:core:*` et `:phone:intent` n'ont pas de dépendance Android ;
- pas de dépendance entre modules de fonctionnalités de même niveau.

## Conséquences

- ✅ Tests JVM rapides du protocole et du moteur d'intentions.
- ✅ Frontières explicites : impossible de mettre de la logique « cerveau » dans la TV par inadvertance.
- ✅ `applicationId` conservés : les utilisateurs mettent à jour sans désinstaller.
- ⚠️ Renommage des dossiers : chemins des APK et du workflow CI à adapter dans la même pull request.

## Amendement — 2026-10-29 (0.3.0-alpha.1)

`:core:testing` n'est pas encore créé : les fixtures de contrat vivent dans `core/protocol/src/test/resources/fixtures/`, seul module qui les consomme aujourd'hui. Le module sera extrait quand la TV ou le téléphone auront leurs propres tests de contrat. La clé de développement est déplacée dans `signing/dev.keystore`, partagée par les deux applications.
