# Lethal Breed - Predator Evolution

<p align="left">
  <img src="https://img.shields.io/badge/Status-BETA-orange?style=for-the-badge&logo=minecraft" alt="Status Beta">
  <img src="https://img.shields.io/badge/Version-1.21.11-brightgreen?style=for-the-badge" alt="Version">
  <img src="https://img.shields.io/badge/Loader-Fabric-blue?style=for-the-badge" alt="Fabric">
</p>

> [!IMPORTANT]
> **This mod is currently in active beta testing.** Minor bugs may occur. Your feedback is essential to refining the AI!

---

> "Zombies are no longer brainless meat sacks. They are now intelligent, adaptive, and relentless hunters."

---

## Redefine the Threat
**Lethal Breed** is a total overhaul of zombie AI for Minecraft Fabric 1.21.11. Forget passive monsters waiting to be hit. Our zombies use a complex **Finite State Machine (FSM)** to analyze their environment, track prey by sound, and overcome any obstacle — whether by mining through your walls or building makeshift bridges.

---

## ![Capabilities](https://img.shields.io/badge/Advanced-Capabilities-darkgreen?style=flat-square)

<details>
<summary><b>AI Mechanics Details</b></summary>

### Dynamic Hearing System
Zombies no longer just see you. They **hear** you.
*   **Sound Reaction:** Footsteps, broken blocks, or explosions attract their attention.
*   **Persistent Tracking:** Once a sound is localized, they will investigate the area even if they lose visual contact.

### Swarm Engineering
No base is safe. Zombies adapt dynamically to the terrain:
*   **Bridging:** If faced with a gap, they will place **dirt** blocks to cross.
*   **Scaffolding:** They can stack up and place **dirt** under their feet to climb.
*   **Tactical Mining:** They analyze obstructions and dig intelligent tunnels towards you.
*   **Crawling Adults:** Baby zombies are gone. Instead, you will face adults capable of crawling through 1-block high gaps.

### Behavioral States (FSM)
Each zombie follows a logic of survival and tracking:
*   **Chase:** Aggressive pursuit and agile climbing.
*   **Distress Call (Panic):** A wounded zombie will scream, sending its exact position to all allies within a 12-block radius. They will converge to form a punishing swarm.
*   **Looting:** Every zombie can pick up and use your equipment. Dying to them often means they will wear your own armor against you.

### Performance & Multithreading
Lethal Breed offloads heavy calculations (block scans, building AI) to separate threads. Hundreds of intelligent zombies can coexist without impacting the server's TPS.
</details>

---

## ![Variants](https://img.shields.io/badge/Special-Variants-red?style=flat-square)

<details>
<summary><b>Mutant and Kamikaze Details</b></summary>

### Biological Variability
The statistics system makes every individual unique and unpredictable:
*   **Scale/Power Correlation:** A zombie's size (Scale) directly multiplies its **Max Health** and **Attack Damage**. The larger they are, the more resilient and deadly they become.
*   **Variable Agility:** Smaller specimens are often faster, compensating for their fragility with frantic speed.

### The Mutant (Rare Boss)
There is a 5% chance (configurable) for a zombie to become a **Mutant**.
*   **Dark Aura:** Recognizable by its squid ink particle aura.
*   **Final Breath:** Upon death, the Mutant explodes, releasing a swarm of minions to avenge its fall.
*   **Boosted Stats:** Larger, stronger, and much more resistant.

### The Kamikaze
Some zombies carry an unstable explosive charge (recognizable by the **TNT** block on their head).
*   **Scalable Detonation:** Explosion power depends on the zombie's size. The bigger it is, the larger the crater will be.
*   **Visual Warning:** Electric sparks and flames appear just before the explosion. Run.
</details>

---

## ![Configuration](https://img.shields.io/badge/Configuration-Guide-blue?style=flat-square)

<details>
<summary><b>Full Parameters (lethalbreed.json)</b></summary>

The file is located in `config/o.a.s/lethalbreed.json`.

### attributes: Physical Statistics
*   `zombieFollowRange` (Default: 18.0): Distance at which a zombie pursues you.
*   `minScale` / `maxScale` (0.85 / 1.35): Random size range for zombies.
*   `minSpeed` / `maxSpeed` (0.18 / 0.28): Ground movement speed.
*   `healthBonusMin` / `healthBonusMax` (0.8 / 1.2): Random health multiplier.

### mutant: Boss Settings
*   `mutantChance` (0.05): Probability of a zombie spawning as a Mutant.
*   `mutantMinionCount` (8): Number of zombies summoned upon Mutant death.
*   `mutantTentacleTickRate` (5): Frequency of aura particle spawning.

### equipment: Equipment Chances
*   `kamikazeChance` (0.05): Probability of a zombie being a kamikaze.
*   `weaponChance` (0.7): Chance of having a weapon in hand.
*   `weaponEnchantChance` (0.4): Chance for the weapon to be enchanted.
*   `armor[Head/Chest/Legs/Feet]Chance`: Probabilities for each armor piece.
*   `armorEnchantChance` (0.3): Chance for the armor to be enchanted.

### ai: Intelligence & Explosions
*   `hearingRange` (16.0): Sound detection radius.
*   `soundLockTicks` (300): Duration a zombie remembers a sound.
*   `kamikazeFuseTicks` (40): Time before kamikaze explosion.
*   `kamikazeExplosionPower` (3.0): Base detonation power.

### panic: Swarm Behavior
*   `healthThreshold` (0.25): Remaining HP to trigger panic (25%).
*   `continueHealthThreshold` (0.5): HP to stop panicking (after regeneration).
*   `screamIntervalTicks` (40): Frequency of alert screams.
*   `allyAlertRange` (12.0): Ally call radius.
*   `stopPackSize` (5): Number of zombies to stop fleeing and counter-attack.
*   `cooldownTicks` (600): Cooldown before panicking again.
*   `fleeExplosionRange` (8.0): Flee distance from explosions.

### movement: Climbing & Building
*   `climbVerticalSpeed` (0.25): Wall climbing speed.
*   `climbHorizontalSpeed` (0.15): Movement speed on walls.
*   `buildGlobalCooldownTicks` (4): Time between each block placement by the group.

### breaking: Block Destruction
*   `breakSpeedMultiplier` (4.0): Mining speed multiplier.
*   `breakMinTicks` (5): Minimum time to break a block.

> *Tip: All these options are modifiable in-game with **Mod Menu**!*
</details>

---

## ![Recommendations](https://img.shields.io/badge/Dependencies-Recommendations-purple?style=flat-square)

### Mandatory
*   [Fabric API](https://modrinth.com/mod/fabric-api)
*   Java 25 (GraalVM recommended)

### O.A.S Recommendations
*   **Mod Menu:** To easily access configuration.
*   **Sodium:** For optimal performance.
*   **Sounds:** Perfectly complements our hearing system.

---

## License & Credits
*   **Developer:** O.A.S (Optimization & Quality)
*   **License:** O.A.S - MS-RSL (Microsoft Reference Source License)
*   **Modpacks:** You are **free** to include Lethal Breed in any modpack, as long as credit is maintained.
*   **Inspiration:** Classic zombie movies and tactical survival games.

---
![O.A.S Badge](https://img.shields.io/badge/O.A.S-Certified-008800?style=for-the-badge)

<sub>*O.A.S - Outbreak Adaptation Series*</sub>
