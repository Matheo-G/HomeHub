# Politique de sécurité

HomeHub pilote une TV sur le réseau local : la sécurité de l'appairage et des commandes est prise au sérieux ([ADR-0004](docs/adr/0004-appairage-et-securite.md)).

## Signaler une vulnérabilité

Merci de **ne pas** ouvrir d'issue publique. Utilisez le [signalement privé GitHub](https://github.com/Matheo-G/HomeHub/security/advisories/new) en décrivant l'impact et les étapes de reproduction. Une première réponse est donnée sous 7 jours.

## Périmètre

- Appairage, jetons, prise de contrôle de la TV depuis le réseau local
- Fuite de secrets (clés d'API) depuis HomeHub Remote
- Service d'accessibilité (« Contrôle complet »)

## Versions prises en charge

Seule la dernière version publiée (release « Dernière version ») reçoit des correctifs jusqu'à la 1.0.
