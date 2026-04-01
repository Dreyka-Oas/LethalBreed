# Structure du Repository GitHub

## Organisation des branches

Les branches sont nommées par version Minecraft :
- `1.21.11` - Code pour Minecraft 1.21.11
- `1.21.4` - Code pour Minecraft 1.21.4 (future)
- `main` - Documentation (README, CURSEFORGE.md, ARCHITECTURE.md)

## Structure d'une branche de version

Chaque branche de version contient les dossiers des loaders supportés :

```
1.21.11/
├── fabric/          # Code Fabric pour 1.21.11
│   ├── build.gradle
│   ├── src/
│   └── ...
├── forge/           # Code Forge pour 1.21.11 (future)
└── ...
```

## Règles de nommage

- **Branche** : Version Minecraft uniquement (ex: `1.21.11`)
- **Dossier** : Nom du loader (ex: `fabric`, `forge`)
- **Chemins de packages** : `oas.work.lethalbreed`

## Branche principale (main)

La branche `main` contient uniquement la documentation :
- `README.md` - Documentation anglophone avec badges
- `CURSEFORGE.md` - Description pour CurseForge (markdown pur)
- `ARCHITECTURE.md` - Ce document
- `LICENSE` - Licence propriétaire

## Ajouter une nouvelle version

1. Créer une nouvelle branche depuis `main` : `git checkout -b 1.21.4`
2. Créer le dossier du loader : `mkdir fabric`
3. Copier le code depuis une version existante
4. Adapter les fichiers de configuration (gradle.properties, build.gradle, etc.)
5. Pousser sur GitHub

## Ajouter un nouveau loader

1. Se placer sur la branche de la version concernée
2. Créer le dossier du loader : `mkdir forge`
3. Implémenter le code pour le nouveau loader
4. Pousser sur GitHub
