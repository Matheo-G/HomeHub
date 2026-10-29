# ADR-0004 — Appairage par code, jetons et sécurité du réseau local

- **Statut** : Acceptée
- **Date** : 2026-10-29

## Contexte

La TV écoute sur le réseau local et peut ouvrir des applications, saisir du texte et, avec le contrôle complet, naviguer partout. Un Wi-Fi domestique accueille des invités et des objets connectés peu sûrs. Il faut empêcher qu'un appareil du réseau prenne le contrôle, sans rendre l'installation pénible.

## Décision

1. **Appairage par code affiché** : un code à 4 chiffres n'est généré que lorsque l'utilisateur ouvre « Associer un téléphone » sur la TV ; il est à usage unique ; 5 essais maximum, puis nouveau code. Seule une personne qui voit l'écran peut appairer.
2. **Jeton par téléphone** : aléatoire 256 bits (`SecureRandom`), remis une fois ; la TV ne stocke que son empreinte SHA-256, associée à l'identifiant aléatoire du téléphone. Révocation globale (V2) puis individuelle (V7).
3. **Session** : aucune commande avant authentification ; taille de message et débit bornés ; sessions inactives fermées.
4. **Secrets** : clés d'API et jetons de services tiers stockés **uniquement** sur le téléphone, chiffrés avec Android Keystore (`EncryptedSharedPreferences` ou DataStore + Tink), jamais journalisés, jamais transmis à la TV.
5. **Chiffrement du transport** : en clair sur le LAN jusqu'à la V6 (les commandes ne contiennent pas de secret) ; **TLS en V7** avec certificat auto-signé généré par la TV et **épinglé** par le téléphone lors de l'appairage (empreinte affichée/échangée avec le code).

## Options écartées

- Pas d'authentification (réseau « de confiance ») : inacceptable pour une application publiée.
- Comptes utilisateurs / serveur central : contraire au principe « local d'abord ».
- TLS dès la V3 : complexité (gestion de certificats sur Android 8, `wss://` avec certificat auto-signé) disproportionnée tant qu'aucune donnée sensible ne circule.

## Conséquences

- ✅ Protection efficace contre les autres appareils du réseau.
- ⚠️ Un attaquant capable d'écouter le Wi-Fi pourrait lire les commandes jusqu'à la V7 ; risque documenté et accepté.
- 🔧 Tests unitaires du gestionnaire d'appairage (essais, expiration, révocation).
