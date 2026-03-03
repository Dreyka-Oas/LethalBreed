# Lethal Breed - Documentation Technique

## Introduction
**Lethal Breed** est un mod de survie-horreur pour Minecraft (Fabric) qui transforme radicalement le comportement des zombies. L'objectif est de faire de chaque nuit un défi stratégique où les zombies ne se contentent pas de marcher vers le joueur, mais adaptent leur approche en fonction de l'environnement.

---

## Fiche Technique
- **Version de Minecraft :** Compatible avec les dernières versions Fabric
- **Loader :** Fabric
- **Langage :** Java
- **Dépendances :** Fabric API
- **Architecture :** Modulaire, basée sur des mixins et une Machine à États Finis (FSM) pour l'IA.

---

## Fonctionnalités Principales

### 1. IA de Construction et de Destruction
Le cœur du mod réside dans la capacité des zombies à surmonter les obstacles physiques.
- **Pontage (Bridging) :** Les zombies peuvent poser des blocs pour franchir des vides ou atteindre des plateformes.
- **Escalade :** Utilise une logique de construction verticale pour "empiler" des blocs ou grimper aux murs naturellement.
- **Minage :** Si un zombie est bloqué par un mur, il commencera à miner les blocs pour se frayer un chemin. La vitesse de minage est personnalisable.
- **Machine à États (BuildStateMachine) :** Gère les transitions entre la poursuite, la construction et le minage.

### 2. Système d'Ouïe
Les zombies réagissent désormais aux sons environnementaux.
- **Événements Détectés :** Pas (Step), pose/destruction de blocs, consommation d'objets, chutes.
- **Logique :** Si un zombie n'a pas de cible visuelle, il se déplacera vers la source du dernier bruit détecté dans un rayon configurable.
- **Registre :** Un `HearingRegistry` stocke les positions sonores par ID d'entité.

### 3. Variabilité des Spécimens (Taille & Stats)
Chaque zombie est unique grâce à une randomisation à l'apparition.
- **Échelle :** Taille variant entre de petits et de grands spécimens.
- **Attributs :** La vitesse et la santé sont corrélées à la taille ou randomisées individuellement.
- **Adultes uniquement :** Le mod désactive les bébés zombies pour privilégier les adultes capables de ramper ou de construire.

### 4. Mutants et Spécialistes
- **Mutants :** Rare chance d'apparition. Ils possèdent une aura de particules ("tentacules") et font apparaître une meute de serviteurs à leur mort.
- **Kamikazes :** Zombies explosifs qui déclenchent une détonation de type Creeper en approchant de leur cible.
- **Équipement Dynamique :** Probabilité accrue de porter des armes, des armures complètes et des enchantements.

### 5. Mécaniques de Panique
Lorsqu'un zombie tombe sous un certain seuil de santé :
- Il peut entrer dans un état de **Panique**.
- Il émet des cris qui alertent les alliés proches.
- Il peut tenter de fuir si sa meute est trop petite.

---

## Architecture du Code

### Paquets Clés
- `oas.work.lethalbreed.ai.builder` : Contient toute la logique complexe de construction et de minage.
- `oas.work.lethalbreed.mixin` : Points d'injection dans le code de Minecraft pour modifier les comportements de base.
- `oas.work.lethalbreed.ai` : Systèmes d'IA secondaires (Ouïe, Kamikaze, Panique).

### Classes Importantes
- `LethalBreed` : Point d'entrée du mod.
- `ModConfig` : Gestionnaire de configuration (format JSON).
- `BuildStateMachine` : Le cerveau derrière les capacités de construction.
- `HearingLogic` : Gère l'interception des `GameEvent` pour le système d'ouïe.
- `MutantLogic` : Logique spécifique aux variantes mutantes.

---

## Configuration (`lethalbreed.json`)
Le mod est hautement personnalisable via son fichier de configuration :
- `zombieFollowRange` : Distance de détection visuelle.
- `mutantChance` : Probabilité d'apparition des mutants.
- `hearingRange` : Sensibilité auditive des zombies.
- `breakSpeedMultiplier` : Multiplicateur de vitesse de destruction des blocs.
- `climbVerticalSpeed` : Vitesse d'ascension pendant la construction.

---

## Installation et Utilisation
1. Installez Fabric Loader.
2. Placez le JAR dans le dossier `mods`.
3. Lancez le jeu pour générer le fichier de config initial dans `config/o.a.s/lethalbreed.json`.
4. Personnalisez les valeurs selon la difficulté souhaitée.

---
*Documentation générée pour le projet Lethal Breed.*
