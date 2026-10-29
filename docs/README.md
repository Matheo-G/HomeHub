# Documentation HomeHub

HomeHub est un système d'exploitation personnel pour Android TV : un launcher léger sur la TV, piloté par une application téléphone qui sert de cerveau (voix, recherche, IA, automatisations).

| Document | Contenu |
|---|---|
| [Vision produit](vision.md) | Pourquoi HomeHub existe, pour qui, principes non négociables |
| [Architecture](architecture.md) | Vue d'ensemble, responsabilités, flux, modules, sécurité, performances |
| [API locale](api/local-api.md) | Protocole TV ↔ téléphone (JSON-RPC 2.0 sur WebSocket), méthodes, événements, erreurs |
| [Roadmap](roadmap.md) | V1 → V10 : périmètre, livrables, critères de sortie |
| [Décisions d'architecture (ADR)](adr/README.md) | Chaque choix structurant, son contexte et ses conséquences |
| [Guide développeur](developer-guide.md) | Installer, compiler, tester, contribuer, étendre |
| [Changelog](changelog.md) | Historique des versions |

## Règles de documentation

- Toute décision structurante (protocole, dépendance majeure, sécurité, découpage en modules) fait l'objet d'une **ADR** avant d'être codée.
- Toute modification du protocole met à jour [l'API locale](api/local-api.md) **dans la même pull request**.
- Toute version publiée a son entrée dans le [changelog](changelog.md).
- Les diagrammes sont en Mermaid, rendus directement par GitHub.
