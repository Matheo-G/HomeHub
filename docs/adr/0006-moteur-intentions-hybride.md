# ADR-0006 — Moteur d'intentions hybride : règles puis IA

- **Statut** : Acceptée
- **Date** : 2026-10-29

## Contexte

« Lance Netflix », « Recherche Interstellar », « Qui est Christopher Nolan ? », « Mets une musique relaxante », « Quelle météo demain ? » demandent des traitements différents. Passer chaque phrase par une IA coûte du temps (1 à 3 s), de l'argent, exige Internet et une clé, et introduit de l'imprévisibilité pour des commandes simples. À l'inverse, des règles seules ne comprennent pas les questions ouvertes.

## Décision

Un pipeline en deux étages dans `:phone:intent` (Kotlin pur) :

1. **Normalisation** : minuscules, accents, ponctuation, formules de politesse, articles.
2. **Règles déterministes** : grammaires par intention (lancer, chercher, jouer, lecture, volume, navigation, saisie, météo). Résultat en < 5 ms, hors ligne.
3. **Repli IA** (si un fournisseur est actif) : classification en intent structuré validé, ou réponse à une question (`Question`).
4. **Sans IA** : une phrase non reconnue devient une recherche universelle (le comportement le plus utile par défaut).

```kotlin
sealed interface Intent {
    data class LaunchApp(val app: AppRef) : Intent
    data class UniversalSearch(val query: String, val scope: SearchScope = SearchScope.All) : Intent
    data class PlayMedia(val query: String, val app: AppRef?) : Intent
    data class PlayMusic(val query: String, val app: AppRef?) : Intent
    data class MediaControl(val action: MediaAction) : Intent
    data class Volume(val change: VolumeChange) : Intent
    data class Navigate(val destination: Destination) : Intent
    data class TypeText(val text: String) : Intent
    data class Weather(val date: LocalDate?, val place: String?) : Intent
    data class Question(val text: String) : Intent
}
```

Le **planificateur** transforme un intent en suite de commandes de l'API locale (ex. `UniversalSearch` → `cards.show(loading)` → sources → `cards.show(results)`).

## Conséquences

- ✅ Les commandes courantes sont instantanées, gratuites, hors ligne et testables.
- ✅ Corpus de phrases versionné, taux de compréhension mesuré en CI (objectif > 85 % sans IA).
- ✅ Le moteur est indépendant d'Android : tests JVM rapides.
- ⚠️ Les règles demandent de la maintenance ; elles sont limitées au français en V3 (anglais en V10).
