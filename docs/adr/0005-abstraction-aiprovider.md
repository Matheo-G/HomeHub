# ADR-0005 — Abstraction `AIProvider` pour des IA interchangeables

- **Statut** : Acceptée
- **Date** : 2026-10-29

## Contexte

HomeHub doit fonctionner avec plusieurs IA (ChatGPT, Claude, Gemini, modèles locaux) sans modifier la TV, et laisser l'utilisateur choisir. Les fournisseurs diffèrent par leurs API, leurs formats de flux, leurs capacités (vision, appel d'outils) et leurs coûts. La V4 ne doit coder qu'un fournisseur de démonstration, mais sans fermer la porte aux autres.

## Décision

Une interface commune dans le module `:phone:ai`, des implémentations isolées, un registre.

```kotlin
interface AIProvider {
    val id: String
    val displayName: String
    val capabilities: Set<AICapability>          // CHAT, STREAMING, INTENT_CLASSIFICATION, TOOLS, VISION
    val requiresApiKey: Boolean

    suspend fun complete(request: AIRequest): AIResponse
    fun stream(request: AIRequest): Flow<AIChunk>             // défaut : complete() en un seul morceau
    suspend fun classify(text: String, context: IntentContext): IntentCandidate?
}

data class AIRequest(
    val messages: List<AIMessage>,      // system / user / assistant
    val context: AssistantContext,      // langue, appli au premier plan, résultats affichés…
    val maxTokens: Int = 512,
)

class AIProviderRegistry(providers: Set<AIProvider>) {
    fun available(): List<AIProvider>
    fun active(): AIProvider              // choisi dans les paramètres, repli sur Demo
}
```

- **V4** : `DemoProvider` — hors ligne, réponses scénarisées et classification par mots-clés ; permet de développer et tester toute la chaîne (voix → carte en flux sur la TV) sans clé ni réseau.
- **Ensuite** : `OpenAIProvider`, `AnthropicProvider`, `GeminiProvider`, `LocalProvider` (modèle exécuté sur le téléphone ou serveur compatible OpenAI sur le réseau local), chacun dans son propre fichier/paquet, ajoutés au registre sans toucher au reste.
- Le **prompt système**, les gabarits et le schéma JSON de classification sont partagés (pas spécifiques à un fournisseur) et versionnés.
- La sortie de `classify` est **validée** (schéma) avant d'agir : une IA ne peut produire que des intents connus, jamais une commande arbitraire.
- Les clés sont lues depuis le coffre chiffré ([ADR-0004](0004-appairage-et-securite.md)).

## Conséquences

- ✅ Ajouter un fournisseur = une classe + une entrée de registre + un écran de configuration générique.
- ✅ Tests de la chaîne complète avec `DemoProvider` et un faux serveur HTTP.
- ⚠️ Le plus petit dénominateur commun : les fonctions spécifiques d'un fournisseur passent par `capabilities`.
- ⚠️ Coûts d'usage à la charge de l'utilisateur (clé personnelle) ; à afficher clairement.
