# Changelog

Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Versionnage : [ADR-0010](adr/0010-workflow-git-et-versionnage.md) — `0.n.0` pour la version `Vn`, `1.0.0` à la V10.

## [Non publié]

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

[Non publié]: https://github.com/Matheo-G/HomeHub/compare/latest...HEAD
[0.2.0]: https://github.com/Matheo-G/HomeHub/releases/tag/latest
