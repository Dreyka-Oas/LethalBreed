# Mod Configuration

The **Lethal Breed** mod is fully customizable via a JSON file located in the configuration folder of your Minecraft instance.

**File Path:** `.minecraft/config/o.a.s/lethalbreed.json`

---

## 📊 Zombie Attributes

These parameters control the basic physical statistics randomly generated for each zombie.

- **`zombieFollowRange`** (Default: `18.0`): Maximum distance a zombie can lock onto a visual target.
- **`minScale` / `maxScale`** (Default: `0.85` / `1.35`): Size range for zombies. Also influences health and damage.
- **`minSpeed` / `maxSpeed`** (Default: `0.18` / `0.28`): Ground movement speed.
- **`healthBonusMin` / `healthBonusMax`** (Default: `0.8` / `1.2`): Additional health multiplier applied after size calculation.

---

## 🧬 Mutation System

Parameters relating to special variants and Mutants.

- **`mutantChance`** (Default: `0.05`): Probability (0.0 to 1.0) that a zombie becomes a Mutant when spawning.
- **`mutantMinionCount`** (Default: `8`): Number of minions that appear when a Mutant dies.
- **`mutantTentacleTickRate`** (Default: `5`): Update frequency of the Mutant's particle aura.

---

## 🔊 AI and Hearing System

Controls hearing sensitivity and tracking capabilities.

- **`hearingRange`** (Default: `16.0`): Radius in blocks within which a zombie can hear noise (footsteps, blocks, etc.).
- **`soundLockTicks`** (Default: `300`): Duration for which a zombie remembers a sound position before resuming its patrol.

---

## 💣 Kamikaze Specialists

- **`kamikazeChance`** (Default: `0.05`): Chance a zombie spawns with TNT on its head.
- **`kamikazeFuseTicks`** (Default: `40`): Time before explosion once primed (20 ticks = 1 second).
- **`kamikazeExplosionPower`** (Default: `3.0`): Explosive power.

---

## 🏃 Panic and Survival

- **`panicHealthThreshold`** (Default: `0.25`): Health threshold (25%) triggering the panic state.
- **`fleeExplosionRange`** (Default: `8.0`): Distance at which zombies move away from an ally about to explode.

---

## 🏗️ Building and Mining

- **`climbVerticalSpeed`** (Default: `0.25`): Ascension speed when climbing or building towers.
- **`breakSpeedMultiplier`** (Default: `4.0`): Block mining speed multiplier (relative to an unarmed player).

---
Last Update: February 12, 2026
