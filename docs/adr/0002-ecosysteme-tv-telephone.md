# ADR-0002 — Deux applications : la TV affiche, le téléphone décide

- **Statut** : Acceptée
- **Date** : 2026-10-29

## Contexte

La box de référence (Xiaomi Mi Box MDZ-16-AB) a 2 Go de RAM, un processeur de 2016 et Android 8/9. HomeHub veut ajouter voix, recherche multi-services, IA et automatisations, sans dégrader la fluidité. Le téléphone de l'utilisateur est plus puissant, mieux connecté, mis à jour plus souvent, et c'est là que se trouvent le micro, le clavier et l'identité de l'utilisateur.

En V2, la TV interprète les phrases (`VoiceCommandInterpreter`) : chaque évolution de la grammaire impose de mettre à jour la TV.

## Options envisagées

1. **Tout sur la TV** — une seule application ; mais TV surchargée, secrets sur un appareil partagé, mises à jour TV lentes (installation manuelle).
2. **Tout sur un serveur domestique** — puissant ; mais un appareil de plus à installer et maintenir, hors de portée du grand public.
3. **TV légère + téléphone cerveau** — la TV exécute et affiche, le téléphone comprend et décide.

## Décision

Option 3.

- **HomeHub TV** : launcher, rendu (cartes, widgets, notifications), navigation, ouverture d'applications, injection d'entrées, serveur réseau local. Aucune interprétation de langage, aucun appel Internet, aucun secret.
- **HomeHub Remote** : télécommande, clavier, voix, moteur d'intentions, IA, appels Internet, paramètres avancés, automatisations.
- La TV expose une API de commandes ([ADR-0003](0003-protocole-json-rpc-websocket.md)) ; le téléphone compose ces commandes.

## Conséquences

- ✅ La TV reste rapide et stable ; l'intelligence évolue au rythme des mises à jour du téléphone.
- ✅ Les clés d'API ne quittent jamais le téléphone personnel.
- ✅ Changer d'IA ou ajouter une source ne touche pas la TV.
- ⚠️ Les fonctions intelligentes nécessitent le téléphone à proximité ; la TV seule reste un launcher complet avec la télécommande physique.
- ⚠️ Deux applications à versionner : le protocole devient un contrat strict (capacités, compatibilité).
- 🔧 V3 : déplacer l'interprétation vocale de la TV vers `:phone:intent`.
