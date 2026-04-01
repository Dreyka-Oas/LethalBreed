# Build & Release SKILL

## Objectif
Quand l'utilisateur demande de "build le .jar" ou de "créer une release", suivre cette procédure.

## Prérequis
- Java 21 installé (GraalVM ou autre)
- GitHub CLI (`gh`) installé et connecté: `gh auth login`

## Étapes de Build

### 1. Configurer Java 21
```powershell
$env:JAVA_HOME = "C:\Users\perso\scoop\apps\graalvm-oracle-21jdk\current"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version  # Vérifier que c'est bien Java 21
```

### 2. Build le mod
```bash
cd 1.21.11/fabric
./gradlew build
```

### 3. Vérifier le .jar
Le .jar se trouve dans: `1.21.11/fabric/build/libs/lethalbreed-2.0.0.jar`

### 4. Créer et pousser le Tag Git
```bash
git tag -a 1.21.11-fabric -m "Release LethalBreed 1.21.11 for Fabric"
git push origin 1.21.11-fabric
```

### 5. Créer la Release GitHub
```bash
gh release create 1.21.11-fabric --title "LethalBreed 1.21.11 (Fabric)" --notes "## LethalBreed 1.21.11 - Fabric

### Features
- Advanced zombie AI with sound detection
- Wall climbing and structure building
- Mutant bosses with minions
- Kamikaze zombies with TNT
- Panic system (zombies alert allies)
- Equipment drops (weapons & armor)
- **NEW:** \`/lethalbreed reload\` command - apply config changes without restarting!

### Configuration
Edit \`config/o.a.s/lethalbreed.json\` to customize all settings.

### Commands
| Command | Description |
|---------|-------------|
| \`/lethalbreed reload\` | Reload config and update all zombies |

### Requirements
- Minecraft 1.21.11
- Fabric Loader
- Fabric API" "1.21.11/fabric/build/libs/lethalbreed-2.0.0.jar"
```

## Notes Importantes
- **Java 21 requis** - Le build échoue avec Java 17 (Fabric Loom nécessite JVM 21+)
- Toujours build AVANT de créer le tag
- Le changelog doit être en ANGLAIS
- Format du tag: `{version}-{loader}` (ex: `1.21.11-fabric`)
- Vérifier que `gh` est connecté: `gh auth status`
