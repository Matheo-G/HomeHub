# ADR-0008 — Interface en cartes pilotée par le protocole

- **Statut** : Acceptée
- **Date** : 2026-10-29

## Contexte

Réponses IA, résultats web, vidéos, musique, widgets, notifications : HomeHub doit les présenter de façon homogène (« tout semble appartenir à HomeHub »). Comme le téléphone décide et évolue plus vite que la TV, coder un écran TV par fonctionnalité obligerait à mettre à jour la TV à chaque nouveauté.

## Options envisagées

1. Écrans TV dédiés par fonctionnalité — riche mais couplé, mises à jour TV fréquentes.
2. Rendu HTML/WebView envoyé par le téléphone — flexible mais lourd sur la Mi Box, incohérent visuellement, surface d'attaque.
3. **Modèle de cartes déclaratif** — le téléphone envoie des données (`Card`), la TV possède un moteur de rendu natif Compose.

## Décision

Option 3.

- Le schéma `Card` / `CardSurface` fait partie de `:core:protocol` et de [l'API locale](../api/local-api.md#43-cartes--cards).
- Un nombre limité de `kind` (answer, web, video, music, app, person, weather, widget, notification) et de `layout` (answer, results, focus, loading).
- **Carte générique** pour tout `kind` inconnu : un téléphone récent reste compatible avec une TV plus ancienne.
- Les actions de cartes ne référencent que des méthodes publiques de l'API ; pas de code, pas de HTML.
- Les mêmes cartes sont prévisualisées sur le téléphone (bibliothèque de rendu partagée à partir de la V5 si pertinent).

## Conséquences

- ✅ Une nouvelle fonctionnalité téléphone (agent, source) s'affiche sur la TV sans mise à jour de celle-ci.
- ✅ Cohérence visuelle et accessibilité centralisées dans un seul moteur.
- ⚠️ Expressivité volontairement limitée : un besoin nouveau passe par l'ajout d'un `kind` (ADR + version de capacité).
