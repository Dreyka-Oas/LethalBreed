# Lethal Breed - Technical Documentation

## Introduction
**Lethal Breed** is a survival-horror mod for Minecraft (Fabric) that radically transforms zombie behavior. The goal is to make every night a strategic challenge where zombies don't just walk toward the player, but adapt their approach based on the environment.

---

## Technical Sheet
- **Minecraft Version:** 1.21.11
- **Loader:** Fabric
- **Language:** Java 25
- **Dependencies:** Fabric API
- **Architecture:** Modular, based on mixins and a Finite State Machine for AI.

---

## Main Features

### 1. Building and Destruction AI
The heart of the mod lies in the zombies' ability to overcome physical obstacles.
- **Bridging:** Zombies can place blocks to cross gaps or reach platforms.
- **Climbing:** Uses vertical construction logic to "stack" blocks or climb walls naturally.
- **Mining:** If a zombie is blocked by a wall, it will start mining blocks to clear a path. Mining speed is configurable.
- **State Machine (BuildStateMachine):** Manages transitions between pursuit, construction, and mining.

### 2. Hearing System
Zombies now react to environmental sounds.
- **Events Detected:** Footsteps (Step), block placement/destruction, eating, falling.
- **Logic:** If a zombie has no visual target, it will move toward the source of the last detected noise within a configurable radius (default: 16 blocks).
- **Registry:** A `HearingRegistry` stores sound positions by entity ID.

### 3. Specimen Variability (Size & Stats)
Every zombie is unique thanks to spawn randomization.
- **Scale:** Size varying between 0.85x and 1.35x.
- **Attributes:** Speed and health are correlated to size or randomized individually.
- **Adults only:** The mod disables baby zombies to favor adults capable of crawling or building.

### 4. Mutants and Specialists
- **Mutants:** 5% spawn chance. They have a particle aura ("tentacles") and spawn a pack of minions upon death.
- **Kamikazes:** Explosive zombies that trigger a Creeper-like detonation when approaching their target.
- **Dynamic Equipment:** Increased probability of carrying weapons, full armor, and enchantments.

### 5. Panic Mechanics
When a zombie falls below 25% health:
- It can enter a **Panic** state.
- It emits screams that alert nearby allies (12-block radius).
- It may attempt to flee if its pack is too small.

---

## Code Architecture

### Key Packages
- `oas.work.lethalbreed.ai.builder`: Contains all complex building and mining logic.
- `oas.work.lethalbreed.mixin`: Injection points into Minecraft code to alter basic behaviors.
- `oas.work.lethalbreed.ai`: Secondary AI systems (Hearing, Kamikaze, Panic).

### Important Classes
- `LethalBreed`: Mod entry point.
- `ModConfig`: Configuration manager (JSON format).
- `BuildStateMachine`: The brain behind the construction capabilities.
- `HearingLogic`: Manages interception of `GameEvent` for the hearing system.
- `MutantLogic`: Logic specific to mutant variants.

---

## Configuration (`lethalbreed.json`)
The mod is highly customizable via its configuration file:
- `zombieFollowRange`: Visual detection distance.
- `mutantChance`: Mutant appearance probability.
- `hearingRange`: Zombie hearing sensitivity.
- `breakSpeedMultiplier`: Block destruction speed multiplier.
- `climbVerticalSpeed`: Ascension speed during construction.

---

## Installation and Usage
1. Install Fabric Loader for version 1.21.11.
2. Place the JAR in the `mods` folder.
3. Launch the game to generate the initial config file in `config/o.a.s/lethalbreed.json`.
4. Customize values according to desired difficulty.

---
*Documentation generated for the Lethal Breed project.*

---
Last Update: February 12, 2026
