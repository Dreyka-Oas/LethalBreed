# Mod Configuration

The **Lethal Breed** mod is fully customizable via a JSON file located in the configuration folder.

**Path:** `.minecraft/config/o.a.s/lethalbreed.json`

---

### 📊 attributes: Physical Statistics
- **`zombieFollowRange`** (18.0): Detection and pursuit distance.
- **`minScale` / `maxScale`** (0.85 / 1.35): Random size range.
- **`minSpeed` / `maxSpeed`** (0.18 / 0.28): Ground movement speed.
- **`healthBonusMin` / `healthBonusMax`** (0.8 / 1.2): Random health multiplier.

---

### 🧬 mutant: Boss Settings
- **`mutantChance`** (0.05): Probability of a zombie spawning as a Mutant.
- **`mutantMinionCount`** (8): Number of zombies summoned upon Mutant death.
- **`mutantTentacleTickRate`** (5): Frequency of aura particle spawning.

---

### ⚔️ equipment: Equipment Chances
- **`kamikazeChance`** (0.05): Probability of a zombie being a kamikaze.
- **`weaponChance`** (0.7): Chance of having a weapon in hand.
- **`weaponEnchantChance`** (0.4): Chance for the weapon to be enchanted.
- **`armorHeadChance`** (0.5): Chance for a helmet.
- **`armorChestChance`** (0.4): Chance for a chestplate.
- **`armorLegsChance`** (0.4): Chance for leggings.
- **`armorFeetChance`** (0.4): Chance for boots.
- **`armorEnchantChance`** (0.3): Chance for the armor to be enchanted.

---

### 🔊 ai: Intelligence & Explosions
- **`hearingRange`** (16.0): Sound detection radius (steps, blocks, falls).
- **`soundLockTicks`** (300): Duration a zombie remembers a sound.
- **`kamikazeFuseTicks`** (40): Time before kamikaze explosion.
- **`kamikazeExplosionPower`** (3.0): Base detonation power.

---

### 📢 panic: Swarm Behavior
- **`healthThreshold`** (0.25): Remaining HP to trigger panic (25%).
- **`continueHealthThreshold`** (0.5): HP to stop panicking.
- **`screamIntervalTicks`** (40): Frequency of alert screams.
- **`allyAlertRange`** (12.0): Ally call radius.
- **`stopPackSize`** (5): Number of zombies to stop fleeing and counter-attack.
- **`cooldownTicks`** (600): Cooldown before panicking again.
- **`fleeExplosionRange`** (8.0): Flee distance from explosions.

---

### 🏃 movement: Climbing & Building
- **`climbVerticalSpeed`** (0.25): Wall climbing speed.
- **`climbHorizontalSpeed`** (0.15): Movement speed on walls.
- **`buildGlobalCooldownTicks`** (4): Time between each block placement by the group.

---

### 🔨 breaking: Block Destruction
- **`breakSpeedMultiplier`** (4.0): Mining speed multiplier.
- **`breakMinTicks`** (5): Minimum time to break a block.
