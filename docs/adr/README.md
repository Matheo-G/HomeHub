# Décisions d'architecture (ADR)

Une ADR (Architecture Decision Record) consigne une décision structurante : son contexte, les options envisagées, le choix et ses conséquences. Format : [MADR](https://adr.github.io/madr/) simplifié, voir le [modèle](0000-modele.md).

| N° | Décision | Statut |
|---|---|---|
| [0001](0001-consigner-les-decisions.md) | Consigner les décisions d'architecture | Acceptée |
| [0002](0002-ecosysteme-tv-telephone.md) | Deux applications : la TV affiche, le téléphone décide | Acceptée |
| [0003](0003-protocole-json-rpc-websocket.md) | Protocole local : JSON-RPC 2.0 sur WebSocket | Acceptée |
| [0004](0004-appairage-et-securite.md) | Appairage par code, jetons et sécurité du réseau local | Acceptée |
| [0005](0005-abstraction-aiprovider.md) | Abstraction `AIProvider` pour des IA interchangeables | Acceptée |
| [0006](0006-moteur-intentions-hybride.md) | Moteur d'intentions hybride : règles puis IA | Acceptée |
| [0007](0007-recherche-universelle.md) | Recherche universelle par métadonnées et deep links | Acceptée |
| [0008](0008-interface-en-cartes.md) | Interface en cartes pilotée par le protocole | Acceptée |
| [0009](0009-modularisation.md) | Modularisation Gradle et règles de dépendance | Acceptée |
| [0010](0010-workflow-git-et-versionnage.md) | Branches de fonctionnalité, CI obligatoire, versions 0.x | Acceptée |
| [0011](0011-injection-entrees-accessibilite.md) | Navigation dans les autres applications via l'accessibilité | Acceptée |

Une ADR acceptée n'est jamais modifiée sur le fond : une décision qui change donne lieu à une nouvelle ADR qui **remplace** l'ancienne (statut « Remplacée par … »).
