# ADR-0001 — Consigner les décisions d'architecture

- **Statut** : Acceptée
- **Date** : 2026-10-29

## Contexte

HomeHub devient un écosystème de plusieurs applications, destiné à l'open source puis au Play Store, et développé sur plusieurs années. Les raisons d'un choix (pourquoi WebSocket, pourquoi l'IA sur le téléphone…) se perdent si elles ne vivent que dans le code ou les conversations.

## Décision

Toute décision structurante est consignée dans `docs/adr/` au format MADR simplifié, **avant** son implémentation, et référencée depuis l'architecture et la pull request qui l'applique.

Est structurante une décision qui touche : le protocole, le découpage en modules, une dépendance majeure, la sécurité ou la vie privée, la répartition TV/téléphone, le workflow de livraison.

## Conséquences

- Les contributeurs comprennent le « pourquoi » sans historique oral.
- Revenir sur une décision demande une nouvelle ADR argumentée.
- Léger coût d'écriture, assumé.
