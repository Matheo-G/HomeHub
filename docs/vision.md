# Vision produit

## En une phrase

**HomeHub transforme une box Android TV en un système personnel, rapide et beau, que l'on pilote naturellement à la voix et depuis son téléphone.**

## Le problème

Les interfaces TV actuelles sont lentes sur le matériel modeste qui équipe la plupart des salons (une Xiaomi Mi Box de 2017 a 2 Go de RAM), encombrées de contenus promotionnels, peu personnalisables, et la recherche y est cloisonnée application par application. La voix existe, mais elle est liée à un seul assistant et ne sait pas agir sur l'ensemble de la maison numérique.

## La réponse HomeHub

| Promesse | Ce que cela veut dire concrètement |
|---|---|
| **Plus rapide que Google TV** | Accueil affiché en moins de 800 ms à froid sur une Mi Box, 60 images/s partout, aucune publicité, aucune requête réseau sur le chemin critique de l'affichage. |
| **Plus beau** | Identité visuelle propre (fond quasi noir, accent personnalisable, cartes arrondies, focus lumineux), animations continues, jamais de transition brutale. |
| **Plus intelligent** | Un moteur d'intentions comprend « Lance Netflix », « Recherche Interstellar », « Qui est Christopher Nolan ? » et choisit seul la bonne action. |
| **Entièrement personnalisable** | Couleurs, fonds, tailles, favoris, widgets, puis thèmes et plugins. |
| **Pilotable à la voix** | Micro du téléphone, transcription en direct, réponses affichées en cartes sur la TV. |
| **Pilotable depuis un téléphone** | Télécommande, clavier, applications, recherche et paramètres avancés. |
| **Pensé pour durer** | Architecture modulaire, protocole versionné et documenté, décisions tracées (ADR), tests automatisés. |

## Principes non négociables

1. **La TV affiche, le téléphone décide.** La TV exécute des commandes simples et rend des cartes ; toute intelligence (interprétation, IA, appels Internet) vit dans le téléphone. Une TV de 2017 reste ainsi fluide en 2030.
2. **Local d'abord.** Le pilotage passe par le réseau local. Aucune donnée n'est envoyée à un serveur HomeHub (il n'y en a pas). Les seuls appels Internet sont ceux que l'utilisateur active (fournisseur d'IA, météo, métadonnées de films), depuis le téléphone.
3. **Vie privée par conception.** Pas de compte, pas de télémétrie par défaut, clés d'API stockées chiffrées sur le téléphone uniquement.
4. **Une seule expérience.** L'utilisateur ne doit jamais sentir qu'il utilise deux applications : mêmes couleurs, mêmes cartes, mêmes mots, transitions continues entre le téléphone et la TV.
5. **Tout est une carte.** Réponse IA, résultat web, vidéo, musique, widget : un seul langage visuel, un seul moteur de rendu.
6. **Dégradation gracieuse.** Sans téléphone, HomeHub reste un excellent launcher avec la télécommande physique. Sans Internet, la télécommande, le clavier et les commandes simples fonctionnent. Sans fournisseur d'IA, les intentions courantes restent comprises.
7. **Ouvert.** Code open source, protocole documenté, fournisseurs d'IA interchangeables, plugins à terme.

## Utilisateurs cibles

- **Le foyer** : allumer la TV, retrouver ses applications et ses contenus en deux secondes, sans manuel.
- **Le passionné** : personnaliser chaque détail, brancher ses services (Plex, NAS, domotique), choisir son IA.
- **Le développeur** : étendre HomeHub par des widgets, des sources de recherche et, à terme, des plugins.

## Non-objectifs

- Remplacer le système Android TV lui-même (HomeHub est une application, sans root).
- Héberger un service cloud HomeHub.
- Contourner les DRM ou les restrictions des applications de streaming.
- Embarquer un modèle d'IA dans la TV.

## Indicateurs de succès

| Indicateur | Cible V10 |
|---|---|
| Démarrage à froid de l'accueil (Mi Box) | < 800 ms |
| Images perdues pendant la navigation | < 1 % |
| Délai touche téléphone → effet TV (Wi-Fi) | < 80 ms |
| Délai fin de phrase → action TV (intention locale) | < 1,5 s |
| Taux d'intentions comprises sans IA (corpus de test) | > 85 % |
| Plantages par 1 000 sessions | < 1 |
