# Structure du Repository GitHub

## Organisation

```
LethalBreed/
├── 1.21.11/           # Branche Git: 1.21.11
│   └── fabric/        # Loader Fabric
│       ├── src/
│       ├── build.gradle
│       └── ...
└── README.md          # Sur branche main
```

## Structure des branches

| Branche | Contenu |
|---------|---------|
| `main` | Documentation (README.md, CURSEFORGE.md, .gitignore) |
| `1.21.11` | Code version Minecraft 1.21.11 avec loader Fabric |

## Comment cloner une version spécifique

```bash
# Cloner le repo complet
git clone https://github.com/Dreyka-Oas/LethalBreed.git

# Se placer sur la version voulue
cd LethalBreed
git checkout 1.21.11
```

## Versions disponibles

| Loader | Versions |
|--------|----------|
| Fabric | 1.21.11 |
| Forge  | - |

## Ajouter une nouvelle version

1. Se placer sur `main` : `git checkout main`
2. Créer une nouvelle branche : `git checkout -b 1.21.4`
3. Créer le dossier du loader : `mkdir fabric`
4. Copier le code depuis une version existante
5. Adapter les fichiers de configuration
6. Commiter et pusher : `git push -u origin 1.21.4`

## Ajouter un nouveau loader

1. Se placer sur la branche de la version concernée
2. Créer le dossier du loader : `mkdir forge`
3. Implémenter le code pour le nouveau loader
4. Commiter et pusher
