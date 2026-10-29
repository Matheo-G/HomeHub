# ADR-0010 — Branches de fonctionnalité, CI obligatoire, versions 0.x

- **Statut** : Acceptée
- **Date** : 2026-10-29

## Contexte

Jusqu'ici, les commits allaient directement sur `main`. Le projet vise l'open source et le Play Store : `main` doit toujours être livrable, chaque changement relu et compilé. Par ailleurs, les APK publiées s'appellent 1.0.0 et 1.1.0 alors que la roadmap prévoit la 1.0 stable à la V10.

## Décision

### Branches
- `main` : toujours vert, toujours livrable ; chaque fusion publie la release « Dernière version ».
- Une branche par fonctionnalité : `feature/v3-protocole-v2`, `fix/…`, `docs/…`, `ci/…`.
- Fusion **uniquement** par pull request, après CI verte (vérification requise sur `main`), en *squash merge* avec un titre au format Conventional Commits.

### Messages de commit
[Conventional Commits](https://www.conventionalcommits.org/fr/) : `feat(tv): …`, `fix(phone): …`, `docs(adr): …`, `ci: …`, `refactor(protocol): …`.

### Versions
- **SemVer 0.x jusqu'à la V10** : la version `Vn` publie `0.n.0`, les correctifs `0.n.p` ; la V10 publie `1.0.0`.
- Les releases déjà publiées (1.0.0 = V1, 1.1.0 = V2) sont renommées 0.1.0 et 0.2.0 dans le changelog ; la prochaine est **0.3.0**. Le `versionCode` (numéro de build CI) reste strictement croissant : Android installe la mise à jour sans désinstallation malgré le `versionName` inférieur.
- Tag `vX.Y.Z` sur `main` → release versionnée ; chaque fusion → release « Dernière version » (pré-version continue).

### CI
- Sur chaque pull request et chaque push sur `main` : tests unitaires, compilation des deux applications, (V3) ktlint, detekt, taille d'APK.
- Les artefacts de pull request permettent de tester une branche sur la Mi Box avant fusion.

## Conséquences

- ✅ Historique lisible, `main` toujours installable, changelog dérivable des commits.
- ⚠️ Un peu plus de cérémonie pour les petits changements (assumé).
