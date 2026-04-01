# 🧟 LethalBreed

### *Zombies are evolving... and they're coming for you.*

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-6B8E23?style=for-the-badge&logo=minecraftexplorer)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Platform-Fabric-orange?style=for-the-badge&logo=fabric)](https://fabricmc.net)
[![License](https://img.shields.io/badge/License-Proprietary-red?style=for-the-badge)](LICENSE)

---

## ⚠️ Description

**LethalBreed** is a horror mod that transforms Minecraft zombies into terrifying strategic predators. They climb walls, build structures, alert their allies, and some even carry explosives!

> *When night falls, survival becomes a memory.*

---

## 🔥 Features

| Feature | Description |
|---------|-------------|
| 🧠 **Advanced AI** | Zombies with sound detection system and strategic behaviors |
| 👂 **Hearing** | They hear your footsteps, block breaking... everything! |
| 🧗 **Climbers** | Zombies climb walls and build structures |
| 👹 **Mutants** | Massive bosses that summon minions |
| 💣 **Kamikazes** | Some carry TNT... boom! |
| 😱 **Panic** | Scream to alert allies and flee in groups |
| ⚔️ **Equipment** | Drop enchanted weapons and armor |

---

## ⚙️ Full Configuration

> ℹ️ **Location** : `config/o.a.s/lethalbreed.json`
>
> 📝 Edit this file to customize the mod.

### 🔄 Reload Command

You can reload the config and apply changes **without restarting the game**:

```
/lethalbreed reload
```

This will:
- Reload the configuration file
- Update all living zombies with new stats (size, speed, health)
- Re-equip zombies with new equipment settings
- Reset AI behaviors (hearing range, panic settings...)

> ⚠️ **Note**: Only zombies spawned *after* the reload will have the new config applied to their initial spawn. Existing zombies are updated with `/lethalbreed reload`.

<p>
  <a href="https://modrinth.com/mod/lethalbreed">
    <img src="https://img.shields.io/badge/Modrinth-00FF00?style=for-the-badge&logo=modrinth&logoColor=black" alt="Modrinth"/>
  </a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/lethal-breed">
    <img src="https://img.shields.io/badge/CurseForge-F16436?style=for-the-badge&logo=curseforge&logoColor=white" alt="CurseForge"/>
  </a>
</p>

---

## 🧟 Category : **ATTRIBUTES**

*Zombie size, speed and base health configuration*

```json
{
  "attributes": {
    "zombieFollowRange": 18.0,
    "minScale": 0.85,
    "maxScale": 1.35,
    "minSpeed": 0.18,
    "maxSpeed": 0.28,
    "healthBonusMin": 0.8,
    "healthBonusMax": 1.2
  }
}
```

| Option | Type | Default | Min | Max | Description |
|--------|------|---------|-----|-----|-------------|
| `zombieFollowRange` | Double | `18.0` | 4.0 | 128.0 | Detection and pursuit range |
| `minScale` | Double | `0.85` | 0.1 | 5.0 | Minimum zombie size |
| `maxScale` | Double | `1.35` | 0.1 | 5.0 | Maximum zombie size |
| `minSpeed` | Double | `0.18` | 0.05 | 1.0 | Minimum movement speed |
| `maxSpeed` | Double | `0.28` | 0.05 | 1.0 | Maximum movement speed |
| `healthBonusMin` | Double | `0.8` | 0.1 | 10.0 | Minimum health multiplier |
| `healthBonusMax` | Double | `1.2` | 0.1 | 10.0 | Maximum health multiplier |

> 💡 **Tip** : Higher scale means bigger zombie but slower (if you lower minSpeed)

<details>
<summary><b>💀 Extreme Configuration Examples</b></summary>

```json
// 🔥 HORDE MODE (fast and small zombies)
"minScale": 0.5,
"maxScale": 0.7,
"minSpeed": 0.25,
"maxSpeed": 0.4

// 👹 NIGHTMARE MODE (massive and slow)
"minScale": 1.5,
"maxScale": 2.5,
"minSpeed": 0.1,
"maxSpeed": 0.15

// 🎲 RANDOM MODE (surprise every spawn)
"minScale": 0.3,
"maxScale": 3.0,
"minSpeed": 0.05,
"maxSpeed": 0.5
```
</details>

---

## 🧟‍♂️ Category : **MUTANT**

*Mutant bosses and their server domination*

```json
{
  "mutant": {
    "mutantChance": 0.05,
    "mutantMinionCount": 8,
    "mutantTentacleTickRate": 5
  }
}
```

| Option | Type | Default | Min | Max | Description |
|--------|------|---------|-----|-----|-------------|
| `mutantChance` | Double | `0.05` | 0.0 | 1.0 | Mutant spawn chance (5% default) |
| `mutantMinionCount` | Int | `8` | 1 | 64 | Number of minions summoned |
| `mutantTentacleTickRate` | Int | `5` | 1 | 20 | Tentacle attack speed |

> ⚠️ **Warning** : A mutant with many minions can lag your server!

<details>
<summary><b>🎮 Recommended Configurations</b></summary>

```json
// 🎯 Balanced (recommended)
"mutantChance": 0.05,
"mutantMinionCount": 4

// 💀 Hardcore
"mutantChance": 0.15,
"mutantMinionCount": 16

// 🏠 Peaceful Server
"mutantChance": 0.01,
"mutantMinionCount": 2
```
</details>

---

## ⚔️ Category : **EQUIPMENT**

*Loot and explosives system*

```json
{
  "equipment": {
    "kamikazeChance": 0.05,
    "weaponChance": 0.7,
    "weaponEnchantChance": 0.4,
    "armorHeadChance": 0.5,
    "armorChestChance": 0.4,
    "armorLegsChance": 0.4,
    "armorFeetChance": 0.4,
    "armorEnchantChance": 0.3
  }
}
```

### 💣 Kamikaze

| Option | Type | Default | Min | Max | Description |
|--------|------|---------|-----|-----|-------------|
| `kamikazeChance` | Double | `0.05` | 0.0 | 1.0 | Chance for zombie to carry TNT |

### ⚔️ Weapons

| Option | Type | Default | Min | Max | Description |
|--------|------|---------|-----|-----|-------------|
| `weaponChance` | Double | `0.7` | 0.0 | 1.0 | Weapon drop chance |
| `weaponEnchantChance` | Double | `0.4` | 0.0 | 1.0 | Enchanted weapon chance |

### 🛡️ Armor

| Option | Type | Default | Min | Max | Description |
|--------|------|---------|-----|-----|-------------|
| `armorHeadChance` | Double | `0.5` | 0.0 | 1.0 | Helmet drop chance |
| `armorChestChance` | Double | `0.4` | 0.0 | 1.0 | Chestplate drop chance |
| `armorLegsChance` | Double | `0.4` | 0.0 | 1.0 | Leggings drop chance |
| `armorFeetChance` | Double | `0.4` | 0.0 | 1.0 | Boots drop chance |
| `armorEnchantChance` | Double | `0.3` | 0.0 | 1.0 | Enchanted armor chance |

> 💎 **Loot** : Zombies can drop iron, diamond, and even Netherite swords!

<details>
<summary><b>📦 Complete Loot Tables</b></summary>

**Possible Weapons** : Iron sword, Diamond sword, Netherite sword, Axe, etc.

**Possible Enchantments** :
- Sharpness, Smite, Bane of Arthropods
- Fire Aspect, Looting
- Knockback, Sweeping Edge

**Possible Armor** : Leather, Iron, Diamond, Netherite (full or partial set)
</details>

---

## 👂 Category : **AI & HEARING**

*Zombie hearing and detection system*

```json
{
  "ai": {
    "hearingRange": 16.0,
    "kamikazeFuseTicks": 40,
    "kamikazeExplosionPower": 3.0,
    "soundLockTicks": 300
  }
}
```

| Option | Type | Default | Min | Max | Description |
|--------|------|---------|-----|-----|-------------|
| `hearingRange` | Double | `16.0` | 0.0 | 64.0 | Zombie hearing distance |
| `kamikazeFuseTicks` | Int | `40` | 10 | 200 | TNT fuse duration in ticks (2 sec ≈ 40 ticks) |
| `kamikazeExplosionPower` | Float | `3.0` | 0.1 | 10.0 | Explosion power |
| `soundLockTicks` | Int | `300` | 60 | 1200 | Sound memory duration (15 sec ≈ 300 ticks) |

> 🔊 **Detected Sounds** : Footsteps, block breaking, door opening, combat...

<details>
<summary><b>🎯 Gameplay Style Configurations</b></summary>

```json
// 👂 ULTRA-DETECTION MODE (they hear EVERYTHING)
"hearingRange": 32.0,
"soundLockTicks": 600

// 🔇 STEALTH MODE (hard to detect)
"hearingRange": 6.0,
"soundLockTicks": 120

// 💣 KAMIKAZE CHAOS MODE
"kamikazeFuseTicks": 20,
"kamikazeExplosionPower": 6.0
```
</details>

---

## 😱 Category : **PANIC**

*Flee and alert behavior*

```json
{
  "panic": {
    "healthThreshold": 0.25,
    "continueHealthThreshold": 0.5,
    "screamIntervalTicks": 40,
    "allyAlertRange": 12.0,
    "stopPackSize": 5,
    "cooldownTicks": 600,
    "fleeExplosionRange": 8.0
  }
}
```

| Option | Type | Default | Min | Max | Description |
|--------|------|---------|-----|-----|-------------|
| `healthThreshold` | Double | `0.25` | 0.0 | 1.0 | Health % to trigger panic |
| `continueHealthThreshold` | Double | `0.5` | 0.0 | 1.0 | Health % to stop panicking |
| `screamIntervalTicks` | Int | `40` | 10 | 200 | Time between panic screams (2 sec) |
| `allyAlertRange` | Double | `12.0` | 0.0 | 32.0 | Ally alert distance |
| `stopPackSize` | Int | `5` | 1 | 32 | Group size to stop panicking |
| `cooldownTicks` | Int | `600` | 100 | 3600 | Cooldown before panic again (30 sec) |
| `fleeExplosionRange` | Double | `8.0` | 0.0 | 16.0 | Explosion flee distance |

> 😱 **Key Feature** : When a zombie panics, it **screams** to alert all nearby zombies!

<details>
<summary><b>🔧 How to disable panic</b></summary>

```json
"healthThreshold": 0.0,
"cooldownTicks": 999999
```
*This virtually disables panic by setting a very low threshold and infinite cooldown.*
</details>

---

## 🧗 Category : **MOVEMENT**

*Wall climbing and structure building system*

```json
{
  "movement": {
    "climbVerticalSpeed": 0.25,
    "climbHorizontalSpeed": 0.15,
    "buildGlobalCooldownTicks": 4,
    "temporaryBlocks": {
      "enabled": true,
      "decayTicks": 600
    }
  }
}
```

| Option | Type | Default | Min | Max | Description |
|--------|------|---------|-----|-----|-------------|
| `climbVerticalSpeed` | Double | `0.25` | 0.01 | 1.0 | Wall climb up speed |
| `climbHorizontalSpeed` | Double | `0.15` | 0.01 | 1.0 | Horizontal wall movement speed |
| `buildGlobalCooldownTicks` | Int | `4` | 1 | 40 | Cooldown between block placements |

### Temporary Blocks

| Option | Type | Default | Min | Max | Description |
|--------|------|---------|-----|-----|-------------|
| `temporaryBlocks.enabled` | Boolean | `true` | - | - | Enable/disable block decay |
| `temporaryBlocks.decayTicks` | Int | `600` | 60 | 7200 | Ticks before blocks disappear (30 sec) |

> 🧱 **Building** : Zombies place dirt/sand blocks to climb up and reach you!
>
> ⏱️ Blocks disappear after 30 seconds by default.

<details>
<summary><b>🏗️ Building Configurations</b></summary>

```json
// 🐌 SLOW BUILD (zombies are slow to build)
"buildGlobalCooldownTicks": 20,
"temporaryBlocks.decayTicks": 1200

// ⚡ FAST BUILD (rapid construction)
"buildGlobalCooldownTicks": 2,
"temporaryBlocks.decayTicks": 300

// 🚫 NO BUILD (disable building)
"buildGlobalCooldownTicks": 999999
```
</details>

---

## 💥 Category : **BREAKING**

*Block breaking speed by zombies*

```json
{
  "breaking": {
    "breakSpeedMultiplier": 4.0,
    "breakMinTicks": 5
  }
}
```

| Option | Type | Default | Min | Max | Description |
|--------|------|---------|-----|-----|-------------|
| `breakSpeedMultiplier` | Double | `4.0` | 0.1 | 100.0 | Mining speed multiplier |
| `breakMinTicks` | Int | `5` | 1 | 100 | Minimum break time |

> ⛏️ A zombie can destroy a dirt block in just a few ticks with these settings!

<details>
<summary><b>📊 Reference Table</b></summary>

| Multiplier | Dirt | Stone | Cobblestone |
|------------|------|-------|-------------|
| 1.0 | 20 ticks | 240 ticks | 48 ticks |
| 4.0 (default) | 5 ticks | 60 ticks | 12 ticks |
| 10.0 | 2 ticks | 24 ticks | 5 ticks |

*1 tick = 0.05 seconds*
</details>

---

## ❓ FAQ

<details>
<summary><b>Does the mod work on multiplayer servers?</b></summary>

✅ **Yes!** LethalBreed is 100% multiplayer compatible. All players will see the same zombie behaviors.
</details>

<details>
<summary><b>How to reset config to default?</b></summary>

Delete the file `config/o.a.s/lethalbreed.json` and restart the game. The file will be recreated with default values.
</details>

<details>
<summary><b>Can I disable specific features?</b></summary>

✅ **Yes!** Each category can be configured independently. Set chances to 0 to disable.
- `kamikazeChance: 0` → No kamikaze zombies
- `mutantChance: 0` → No mutants
- `weaponChance: 0` → No weapon drops
</details>

<details>
<summary><b>Is the mod compatible with other mob mods?</b></summary>

⚠️ **Partially.** Behaviors may apply to zombies added by other mods, but interactions are not guaranteed.
</details>

<details>
<summary><b>How to report a bug?</b></summary>

Report bugs on <a href="https://github.com/Dreyka-Oas/LethalBreed/issues">GitHub Issues</a>, <a href="https://modrinth.com/mod/lethalbreed">Modrinth</a> or <a href="https://www.curseforge.com/minecraft/mc-mods/lethal-breed">CurseForge</a>.
</details>

---

## 📜 License

**Proprietary**
