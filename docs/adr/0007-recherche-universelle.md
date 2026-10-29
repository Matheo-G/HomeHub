# ADR-0007 — Recherche universelle par métadonnées et deep links

- **Statut** : Acceptée
- **Date** : 2026-10-29

## Contexte

« Recherche Interstellar » doit chercher sur Netflix, Plex, Prime Video et YouTube, puis afficher les résultats. Contrainte majeure : **Netflix et Prime Video n'offrent aucune API publique de recherche** et leurs applications TV n'acceptent pas toutes l'intention de recherche Android. Lire leur catalogue par extraction de pages violerait leurs conditions d'utilisation.

## Options envisagées

1. Recherche globale Android TV (intention système) — gratuite, mais résultats affichés par Google, hors de l'interface HomeHub.
2. Extraction des catalogues (scraping) — fragile et contraire aux conditions d'utilisation. **Écartée.**
3. **Métadonnées ouvertes + disponibilité par service + ouverture dans l'application** — légal, stable, riche.

## Décision

Option 3, côté téléphone, derrière une interface commune :

```kotlin
interface SearchSource {
    val id: String
    suspend fun search(query: String, context: SearchContext): List<SearchResult>
}
```

| Source | Fournit | Accès |
|---|---|---|
| `TmdbSource` | Films, séries, personnes, affiches, **fournisseurs de streaming par pays** (Netflix, Prime Video, Disney+… pour la France) | API TMDB (clé gratuite) |
| `YouTubeSource` | Vidéos | YouTube Data API v3 (clé, quota) |
| `PlexSource` | Contenus du serveur Plex personnel | API du serveur Plex (jeton de l'utilisateur, réseau local) |
| `InstalledAppsSource` | Applications installées sur la TV | Protocole local (`app.list`) |

- Agrégation **en parallèle** avec délai maximal (2,5 s), résultats affichés au fil de l'eau, déduplication (titre + année), classement (correspondance, disponibilité sur un service installé, popularité).
- **Ouverture** d'un résultat, par ordre de préférence : deep link connu → intention de recherche de l'application avec le titre → ouverture de l'application + saisie du titre (contrôle complet) → ouverture simple.
- Les sources sont activables individuellement ; sans clé, la recherche se limite aux applications installées et à la recherche globale.

## Conséquences

- ✅ Résultats riches (affiches, années, « Disponible sur Netflix ») dans l'interface HomeHub.
- ✅ Respect des conditions d'utilisation des services.
- ⚠️ La disponibilité vient de TMDB (données JustWatch) : peut avoir quelques jours de retard ; mention de la source affichée, comme l'exigent les conditions de TMDB.
- ⚠️ L'ouverture « directement sur le film » dépend de chaque application ; le repli garantit au minimum l'arrivée dans la bonne application.
- ⚠️ Quotas d'API (YouTube notamment) : cache des résultats sur le téléphone.

## Amendement — 2026-10-29 (0.3.0-alpha.2)

Première livraison : `TmdbSource` (clé saisie par l'utilisateur, coffre chiffré) et `InstalledAppsSource`. `YouTubeSource` (clé et quota YouTube Data API) et `PlexSource` (jeton du serveur) sont reportées au lot 3.3b. En attendant, chaque recherche propose des cartes « Chercher dans… » (Netflix, YouTube, Prime Video, Plex, Disney+) qui lancent la recherche **dans** l'application installée : la promesse « chercher sur Netflix, Plex, Prime Video et YouTube » est tenue sans clé supplémentaire. Ouvrir un film « Disponible sur Netflix » lance la recherche du titre dans Netflix (TMDB ne fournit pas les identifiants internes des services).
