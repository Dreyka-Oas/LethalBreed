# Build & Release SKILL

## Objectif
Quand l'utilisateur demande de "build le .jar" ou de "créer une release", suivre cette procédure.

## Étapes de Build

### 1. Build le mod
```bash
cd 1.21.11/fabric
./gradlew build
# ou sur Windows:
gradlew.bat build
```

### 2. Trouver le .jar
Le .jar se trouve dans: `1.21.11/fabric/build/libs/`

### 3. Créer un Tag Git
```bash
git tag -a 1.21.11-fabric -m "Release LethalBreed 1.21.11 for Fabric"
git push origin 1.21.11-fabric
```

### 4. Créer une Release GitHub
Utiliser `gh release create` avec:
- **Tag**: `1.21.11-fabric`
- **Title**: `LethalBreed 1.21.11 (Fabric)`
- **Body**: Changelog en anglais

## Changelog Template

```markdown
## LethalBreed 1.21.11 - Fabric

### Features
- Advanced zombie AI with sound detection
- Wall climbing and structure building
- Mutant bosses with minions
- Kamikaze zombies with TNT
- Panic system (zombies alert allies)
- Equipment drops (weapons & armor)
- **NEW:** `/lethalbreed reload` command - apply config changes without restarting!

### Configuration
Edit `config/o.a.s/lethalbreed.json` to customize:
- Zombie attributes (size, speed, health)
- Mutant spawn chance
- Kamikaze & equipment drops
- AI hearing range
- Panic behavior
- Movement & building speed

### Commands
| Command | Description |
|---------|-------------|
| `/lethalbreed reload` | Reload config and update all zombies |

### Installation
1. Install Fabric Loader
2. Install Fabric API
3. Drop `lethalbreed-1.21.11.jar` into `mods/` folder

### Requirements
- Minecraft 1.21.11
- Fabric Loader
- Fabric API
```

## Notes
- Toujours build AVANT de créer le tag
- Toujours vérifier que le .jar existe avant de créer la release
- Le changelog doit être en ANGLAIS
- Utiliser le format: `{version}-{loader}` pour le tag (ex: `1.21.11-fabric`)
