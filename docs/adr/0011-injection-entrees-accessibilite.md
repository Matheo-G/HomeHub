# ADR-0011 — Navigation dans les autres applications via l'accessibilité

- **Statut** : Acceptée
- **Date** : 2026-10-29

## Contexte

Pour que la télécommande téléphone pilote Netflix ou YouTube (flèches, OK, Retour, saisie), la TV doit agir dans une autre application. Android interdit l'injection d'événements clavier entre applications (`INJECT_EVENTS` est réservé au système). La Mi Box n'est pas rootée et l'utilisateur n'a pas ADB.

## Options envisagées

1. Root ou permission système — impossible sur un appareil grand public.
2. ADB local (connexion de la TV à son propre `adbd`) — puissant, mais exige le débogage réseau activé et une autorisation fragile ; inadapté au grand public et refusé par le Play Store.
3. **Service d'accessibilité** — API officielle, activée explicitement par l'utilisateur.

## Décision

Option 3, en fonction **optionnelle** (« Contrôle complet ») :
- Retour et Accueil par actions globales ;
- Android 13+ : actions globales natives du pavé directionnel ;
- Android 8–12 : déplacement du focus et clic par l'arbre d'accessibilité ;
- saisie de texte dans le champ actif.

Sans contrôle complet : HomeHub reste pilotable entièrement, et partout pour les médias, le volume, l'ouverture d'applications et la voix.

## Conséquences

- ✅ Aucun root, aucun ADB, activation en quelques clics à la télécommande.
- ⚠️ Avant Android 13, la navigation est partielle dans les applications qui dessinent leur propre interface (lecteurs vidéo) ; documenté pour l'utilisateur.
- ⚠️ Play Store : l'usage de l'API d'accessibilité hors aide aux personnes handicapées impose une déclaration et une divulgation explicite. Prévu en V10 ; si refus, variante Play sans contrôle complet.
- 🔒 Le service ne lit l'écran qu'au moment d'une commande et ne transmet aucun contenu lu.
