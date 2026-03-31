# LethalBreed

**Zombies are evolving... and they're coming for you.**

Minecraft 1.21.11 | Fabric

---

## Description

**LethalBreed** is a horror mod that transforms Minecraft zombies into terrifying strategic predators. They climb walls, build structures, alert their allies, and some even carry explosives!

*When night falls, survival becomes a memory.*

---

## Features

- **Advanced AI** - Zombies with sound detection system and strategic behaviors
- **Hearing** - They hear your footsteps, block breaking... everything!
- **Climbers** - Zombies climb walls and build structures
- **Mutants** - Massive bosses that summon minions
- **Kamikazes** - Some carry TNT... boom!
- **Panic** - Scream to alert allies and flee in groups
- **Equipment** - Drop enchanted weapons and armor

---

## Configuration

Configuration file location: `config/o.a.s/lethalbreed.json`

### Attributes

Zombie size, speed and base health configuration.

| Option | Default | Description |
|--------|---------|-------------|
| zombieFollowRange | 18.0 | Detection and pursuit range (4.0-128.0) |
| minScale | 0.85 | Minimum zombie size (0.1-5.0) |
| maxScale | 1.35 | Maximum zombie size (0.1-5.0) |
| minSpeed | 0.18 | Minimum movement speed (0.05-1.0) |
| maxSpeed | 0.28 | Maximum movement speed (0.05-1.0) |
| healthBonusMin | 0.8 | Minimum health multiplier (0.1-10.0) |
| healthBonusMax | 1.2 | Maximum health multiplier (0.1-10.0) |

### Mutant

Mutant bosses and their server domination.

| Option | Default | Description |
|--------|---------|-------------|
| mutantChance | 0.05 | Mutant spawn chance (0.0-1.0) |
| mutantMinionCount | 8 | Number of minions summoned (1-64) |
| mutantTentacleTickRate | 5 | Tentacle attack speed (1-20) |

### Equipment

Loot and explosives system.

| Option | Default | Description |
|--------|---------|-------------|
| kamikazeChance | 0.05 | Chance for zombie to carry TNT (0.0-1.0) |
| weaponChance | 0.7 | Weapon drop chance (0.0-1.0) |
| weaponEnchantChance | 0.4 | Enchanted weapon chance (0.0-1.0) |
| armorHeadChance | 0.5 | Helmet drop chance (0.0-1.0) |
| armorChestChance | 0.4 | Chestplate drop chance (0.0-1.0) |
| armorLegsChance | 0.4 | Leggings drop chance (0.0-1.0) |
| armorFeetChance | 0.4 | Boots drop chance (0.0-1.0) |
| armorEnchantChance | 0.3 | Enchanted armor chance (0.0-1.0) |

### AI & Hearing

Zombie hearing and detection system.

| Option | Default | Description |
|--------|---------|-------------|
| hearingRange | 16.0 | Zombie hearing distance (0.0-64.0) |
| kamikazeFuseTicks | 40 | TNT fuse duration in ticks (10-200) |
| kamikazeExplosionPower | 3.0 | Explosion power (0.1-10.0) |
| soundLockTicks | 300 | Sound memory duration in ticks (60-1200) |

### Panic

Flee and alert behavior.

| Option | Default | Description |
|--------|---------|-------------|
| healthThreshold | 0.25 | Health % to trigger panic (0.0-1.0) |
| continueHealthThreshold | 0.5 | Health % to stop panicking (0.0-1.0) |
| screamIntervalTicks | 40 | Time between panic screams (10-200) |
| allyAlertRange | 12.0 | Ally alert distance (0.0-32.0) |
| stopPackSize | 5 | Group size to stop panicking (1-32) |
| cooldownTicks | 600 | Cooldown before panic again (100-3600) |
| fleeExplosionRange | 8.0 | Explosion flee distance (0.0-16.0) |

### Movement

Wall climbing and structure building system.

| Option | Default | Description |
|--------|---------|-------------|
| climbVerticalSpeed | 0.25 | Wall climb up speed (0.01-1.0) |
| climbHorizontalSpeed | 0.15 | Horizontal wall movement speed (0.01-1.0) |
| buildGlobalCooldownTicks | 4 | Cooldown between block placements (1-40) |
| temporaryBlocks.enabled | true | Enable/disable block decay |
| temporaryBlocks.decayTicks | 600 | Ticks before blocks disappear (60-7200) |

### Breaking

Block breaking speed by zombies.

| Option | Default | Description |
|--------|---------|-------------|
| breakSpeedMultiplier | 4.0 | Mining speed multiplier (0.1-100.0) |
| breakMinTicks | 5 | Minimum break time (1-100) |

---

## How to Disable Features

Set chances to 0 to disable:
- kamikazeChance: 0 = No kamikaze zombies
- mutantChance: 0 = No mutants
- weaponChance: 0 = No weapon drops

---

## FAQ

**Does the mod work on multiplayer servers?**
Yes! LethalBreed is 100% multiplayer compatible.

**How to reset config to default?**
Delete `config/o.a.s/lethalbreed.json` and restart the game.

**Is the mod compatible with other mob mods?**
Partially. Behaviors may apply to zombies added by other mods.

---

## License

**Proprietary** - © 2024 LethalBreed
