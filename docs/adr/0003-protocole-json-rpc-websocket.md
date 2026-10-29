# ADR-0003 — Protocole local : JSON-RPC 2.0 sur WebSocket

- **Statut** : Acceptée (remplace le protocole v1 TCP de la V2)
- **Date** : 2026-10-29

## Contexte

Le protocole v1 (V2) est un flux TCP de lignes JSON avec des types de messages ad hoc. Il fonctionne, mais : pas de corrélation requête/réponse (on ne sait pas quelle commande a échoué), pas de convention d'erreurs, pas d'outillage standard pour le tester, et il faut écrire soi-même le découpage des trames et le maintien de connexion.

Le cahier des charges demande une API locale documentée, une couche de commandes unique, et recommande WebSocket.

## Options envisagées

| Option | Pour | Contre |
|---|---|---|
| TCP + JSON maison (v1) | Déjà en place, zéro dépendance | Corrélation, erreurs, outillage à réinventer |
| HTTP REST local | Universel, simple à tester (curl) | Pas d'événements TV → téléphone sans polling ou SSE |
| gRPC | Typage fort, flux | Lourd sur la TV (HTTP/2, protobuf), difficile à tester à la main |
| **WebSocket + JSON-RPC 2.0** | Bidirectionnel, trames gérées, standard documenté, `id` de corrélation, codes d'erreur, testable avec `websocat` | Une dépendance serveur sur la TV |
| MQTT | Excellent pour la domotique | Nécessite un broker |

## Décision

**JSON-RPC 2.0 sur WebSocket**, spécifié dans [l'API locale](../api/local-api.md).

- Serveur TV : **Java-WebSocket** (`org.java-websocket`, ~150 Ko, sans dépendance, éprouvé sur Android 5+). Ktor serveur a été écarté pour son poids (plusieurs Mo) au regard du budget TV de 3 Mo.
- Client téléphone : **OkHttp** WebSocket (standard Android, reconnexion maîtrisée).
- Sérialisation : **kotlinx.serialization**, dans le module Kotlin pur `:core:protocol` partagé par les deux applications.
- Méthodes `domaine.action`, événements en notifications JSON-RPC, capacités annoncées dans `session.hello`.
- Toutes les méthodes aboutissent au `CommandDispatcher` de la TV : c'est la « couche unique ».

## Conséquences

- ✅ Chaque commande a une réponse ou une erreur explicite ; le téléphone peut afficher la bonne raison d'un échec.
- ✅ Les cartes d'IA peuvent être mises à jour en flux (notifications successives).
- ✅ Débogage avec des outils standards.
- ⚠️ Migration : v1 et v2 coexistent pendant la V3 (ports/chemins distincts), v1 retiré en V4.
- ⚠️ Ajout d'une dépendance TV (~150 Ko), mesurée par le contrôle de taille d'APK en CI.

## Amendement — 2026-10-29 (0.3.0-alpha.1)

Le protocole v1 est **retiré immédiatement** au lieu de coexister jusqu'à la V4. Raisons : les deux applications sont publiées ensemble dans la même release, le parc v1 se limite à l'appareil du mainteneur, et une coexistence aurait imposé deux serveurs sur deux ports avec deux annonces DNS-SD, au prix d'une complexité sans utilisateur. Conséquence : il faut mettre à jour la TV et le téléphone ensemble ; le téléphone ne propose que les TV annonçant `proto=2`.
