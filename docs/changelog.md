# Changelog

Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Versionnage : [ADR-0010](adr/0010-workflow-git-et-versionnage.md) — `0.n.0` pour la version `Vn`, `1.0.0` à la V10.

## [Non publié]

## [0.3.0-alpha.2] — 2026-10-29 · V3, lots 3.3 et 3.4 — Recherche universelle

### Ajouté
- « Recherche Interstellar » (ou toute phrase non reconnue) affiche sur la TV des **cartes de résultats** : films et séries avec affiche, année, résumé et services où ils sont disponibles en France ; personnes ; applications ; et « Chercher dans… » Netflix, YouTube, Prime Video, Plex, Disney+.
- OK sur une carte : le téléphone ouvre le bon service et y lance la recherche du titre (ou la bande-annonce sur YouTube si aucun service installé ne le propose).
- Téléphone : module `:phone:sources` (TMDB, applications installées, agrégation parallèle), écran « Sources de recherche » avec clé TMDB vérifiée et chiffrée (Android Keystore).
- TV : moteur de cartes (`cards.show/update/dismiss`, événements `card.action` / `cards.dismissed`), chargement animé, détail de la carte focalisée, fermeture par Retour ou après 2 minutes.
- Tests : contrat des cartes et événements, état des cartes TV, sources sur serveur TMDB simulé.

### Modifié
- Sauvegarde Android désactivée sur le téléphone (jetons et clés chiffrés liés à l'appareil).

## [0.3.0-alpha.1] — 2026-10-29 · V3, lots 3.1 et 3.2

> ⚠️ Mettez à jour **la TV et le téléphone ensemble** : le protocole v1 n'est plus pris en charge. Les associations existantes sont conservées.

### Ajouté
- Protocole local v2 : JSON-RPC 2.0 sur WebSocket, module partagé `:core:protocol` (kotlinx.serialization), tests de contrat sur des exemples JSON.
- TV : couche de commandes unique (`CommandDispatcher`, handlers par domaine) utilisée par le réseau et par l'interface ; `app.open` avec replis (recherche dans l'application, ouverture simple, recherche globale) ; limitation de débit par session.
- Téléphone : moteur d'intentions `:phone:intent` et planificateur `IntentExecutor` — la phrase dictée est comprise sur le téléphone, la TV n'exécute que des commandes ; corpus de 69 phrases mesuré à chaque build.
- CI : tests du protocole, du moteur d'intentions et de la TV ; budgets de taille des APK (TV < 3 Mo, téléphone < 8 Mo).

### Modifié
- Modules renommés `:tv`, `:phone`, `:phone:intent`, `:core:protocol` ; identifiants d'application inchangés.
- Version unique des deux applications (`homehub.version`) ; numérotation 0.x jusqu'à la V10.

### Supprimé
- Protocole v1 (TCP) et interprétation vocale sur la TV.

## [0.2.1] — 2026-10-29 · Documentation

### Ajouté
- Documentation de référence : vision produit, architecture cible, API locale v2 (JSON-RPC 2.0 sur WebSocket), roadmap V1 → V10, guide développeur, 11 décisions d'architecture (ADR).
- Modèles d'issues et de pull requests, `CONTRIBUTING.md`, `SECURITY.md`.

### Modifié
- Workflow Git : développement par branches et pull requests, fusion dans `main` uniquement avec une CI verte.

## [0.2.0] — 2026-10-29 · V2 Télécommande

Publiée sous le numéro **1.1.0** (TV, build 5) et **1.0.0** (téléphone).

### Ajouté
- **HomeHub Remote**, application téléphone : découverte automatique de la TV (mDNS), appairage par code à 4 chiffres et QR code, pavé directionnel circulaire, lecture et volume, liste des applications de la TV avec icônes, clavier synchronisé en direct, micro intégré avec transcription en direct.
- TV : serveur de télécommande (service de premier plan, relancé au démarrage), commandes vocales en français, bandeau de confirmation, « Contrôle complet » par service d'accessibilité, section *Télécommande mobile* dans les paramètres.
- Protocole v1 (TCP, JSON par ligne) dans le module `:protocol`.
- CI : tests unitaires de la grammaire vocale, deux APK, release « Dernière version » à téléchargement direct.

## [0.1.0] — 2026-10-28 · V1 Launcher

Publiée sous le numéro **1.0.0**.

### Ajouté
- Launcher Android TV (`HOME`, `LEANBACK_LAUNCHER`), compatible Android 8+ et Xiaomi Mi Box MDZ-16-AB.
- Accueil : heure, date, état réseau, favoris (Netflix, YouTube, Deezer, Plex, Prime Video, Paramètres, Toutes les applications), applications récentes.
- Cartes avec focus animé (agrandissement, ombre, halo, contour lumineux), fond animé discret.
- Paramètres persistants : couleur d'accent, fond d'écran, taille des cartes.
- CI GitHub Actions : APK release à chaque push sur `main`.

