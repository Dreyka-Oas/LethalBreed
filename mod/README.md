<div align="center">

# 🧟 LethalBreed

### Vanilla zombies become a relentless, environment-aware threat.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-5b8731?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-Loader%200.19.3-dbb69b?style=for-the-badge&logo=fabric&logoColor=white)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-21-b07219?style=for-the-badge&logo=openjdk&logoColor=white)](https://bell-sw.com/)
[![Last commit](https://img.shields.io/github/last-commit/Dreyka-Oas/LethalBreed?style=for-the-badge&color=8b0000&labelColor=1a1a1a)](https://github.com/Dreyka-Oas/LethalBreed/commits/master)
[![Issues](https://img.shields.io/github/issues/Dreyka-Oas/LethalBreed?style=for-the-badge&color=8b0000&labelColor=1a1a1a)](https://github.com/Dreyka-Oas/LethalBreed/issues)

They navigate with a per-dimension flow field, **pillar up** to reach perched targets, **descend**
(carve or build a staircase) to targets below, **break and bridge** terrain in the way, **see and hear**
(through real noise, not walls), **swim and dive** through water, **remember** a target's last-known spot,
and hunt the nearest living entity — all built to scale toward **~1000 active zombies**. Heavy pathfinding
runs off the server thread, with an optional **AMD GPU (OpenCL)** compute path and a multithreaded CPU
fallback.

</div>

<br>

<div align="center">

| ⛏️ Vertical pursuit | 🌊 Water | 👁️ Targeting | ☠️ Contamination | 📈 Endless phases | ⚡ GPU compute |
|:---:|:---:|:---:|:---:|:---:|:---:|
| pillar up / descend | float · dive · break | sight + sound + memory | wasting plague | unbounded escalation | OpenCL + CPU fallback |

</div>

---

## Table of contents

- [Toolchain](#-toolchain)
- [Features](#-features)
- [Build & run](#-build--run)
- [Config](#-config)
- [Commands](#-commands)
- [Project layout](#-project-layout)
- [In-game test](#-in-game-test)

---

## 🔧 Toolchain

| Thing | Value |
|-------|-------|
| Minecraft | `1.21.11` (Mojang mappings) |
| Java | `21` — **BellSoft Liberica NIK 23.1.4** (GraalVM JIT) |
| Loader / API | `fabric-loader 0.19.3` / `fabric-api 0.141.4+1.21.11` |
| Loom / Gradle | `1.17.12` / `9.5.1` (wrapper) |
| GPU compute | any AMD GPU via OpenCL/JOCL (auto-used when present; CPU fallback) |

> The build JDK is pinned in `gradle.properties` via `org.gradle.java.home` — update that path if the JDK moves.

---

## 🩸 Features

### ⛏️ Vertical pursuit — jump-and-place pillar
A zombie whose target is perched close above and out of reach **pillars up like a player**: it jumps
(real upward velocity impulse), drops a dirt support into the cell it left, lands one block higher, and
repeats until it reaches the target (or hits `pillarMaxHeight`). It faces the target while climbing. The
column is auto-removed by the placed-block tracker. Triggers on sight of an overhead target — no need to
be provoked. (Notably: `setJumping`/`setPos` do **not** work for this from the tick scheduler — see the
`zombie-ascend-jump-and-place-not-setpos` skill.) A live **Jump Boost** effect is folded into the impulse
dynamically (`+0.1 × (amplifier+1)`, the vanilla rule) — not hard-coded — so a boosted zombie jumps higher.

### ⛏️ Vertical pursuit — descend to targets below
The mirror of the pillar: a zombie whose target is below comes **down** to it at any depth, choosing the
safe option layer by layer — walk short drops, **carve a staircase** through solid terrain toward the
target, take a safe straight-down shaft, or **build a descending dirt staircase** out over open air (place
first, wait for the block to exist, then step — toward the target's side, never the wrong way). It never
breaks the last block over a deep void, so it won't plummet. See `zombie-descend-carve-and-build-staircase`.

### 🌊 Water — float, dive, break
Zombies can't drown, so in water they:
- **surface gently** and hold at the top (no fast pop, no dirt towers),
- **dive** after a target that is itself submerged below them,
- **swim straight** at the target (driven directly, facing it — no nav-induced spinning), and
- **break** solid blocks between them and the target (and the floor below when diving).

Added via a vanilla `FloatGoal` plus a per-tick swim pass. Config: `floatInWater`, `waterRiseSpeed`,
`waterDiveSpeed`, `waterSwimSpeed`.

### 👁️ Targeting — nearest detected, sight or noise, with memory
Each tick a zombie acquires the **nearest valid living entity** it can **detect** within
`targetDetectRadius` and switches to a closer one as it appears. Detection is **sight OR hearing**:
- **Sight** is an opaque-only line of sight — glass/ice/leaves are see-through, stone blocks it.
- **Hearing** passes through walls but only for an entity actually **making noise** this tick — walking
  (not sneaking), acting (attack/place/break/mine, eat/drink), or just hurt. A motionless, silent entity
  emits no sound, so it's sight-only — no more detecting a trapped villager through a wall.

The nearest **detected** entity wins, so a close hidden-but-audible entity beats a far visible one. When a
target slips out of both sight and hearing, the zombie keeps the **last-known position** in short-term
memory (`targetMemoryTicks`, ~10 s) and heads there before giving up; any live detection overrides it
instantly. Excluded: other zombies, bosses (dragon/wither), armor stands, and creative/spectator players.

### 🧱 Block ops, sound, LOD
Reactive break/bridge with a per-tick budget; placed dirt auto-removed after `placedBlockLifetimeTicks`
(no drop). Player footsteps (movement-gated, sneaking is silent) and block-breaks emit sound events that
attract zombies. A 4-tier LOD (`HIGH`/`MEDIUM`/`LOW`/`FROZEN`) throttles distant zombies.

### 🥚 Spawn control & variation
Baby zombies and drowned are discarded; equipment is stripped (config-gated). Each zombie gets a small,
UUID-deterministic size/speed/damage/leap variation — applied in `Zombie.finalizeSpawn` (**before** the
client sees the entity) so there is no visible resize on spawn.

### ☠️ Super Contamination — the wasting plague
A zombie hit can infect any non-zombie living entity (chance rises with the phase), applying **Super
Contamination** (skull icon). It deals **ramping wither damage to death** and drains a player's hunger
faster and faster. **Milk doesn't cure it** — a persistent counter re-applies it. The only escape is to
**stay crouched**: each check has a tiny random chance (5–8%) to shake it off. A contaminated victim simply
**dies** from the plague — nothing is reanimated. Config: `contamination*`.

### 😨 Zombie mood — celebrate / flee / regen
Real zombies react to the fight. After a **direct kill that clears the area** (no other prey nearby) a
zombie **raises its arms and lets out a loud triumphant groan**. A zombie that drops **below 1⁄3 health**
while a threat is around **flees** directly away from it; once far enough it **screams for help**, a sound
that **rallies nearby idle zombies** to its position. While fleeing (or celebrating) and still hurt it
**slowly self-heals** (0.5 HP / 5 s) until it climbs back to **1⁄2 health**, at which point it stops fleeing
and rejoins the hunt. Config: `mood*` / `flee*` / `regen*` / `distress*` / `celebrate*`.

### 🎭 Special zombie variants
Each spawn may roll **one of 8 special types** (chance scales with the phase; harder types unlock at higher
phases via each type's `unlockPhase`), shown as a floating name. `Kind` decides where the behaviour lives —
**PASSIVE** = spawn-time buffs only, **ACTIVE** = a per-tick action, **DEATH** = fires on death:

| Kind | Types |
|------|-------|
| Passif | *Sprinteur* (rapide), *Bondisseur* (bond/pounce), *Juggernaut* (blindé / gros PV) |
| Actif | *Bombeur* (explose près de la cible), *Hurleur* (aggro la horde), *Soigneur* (regen de zone), *Nécromancien* (invoque des renforts) |
| À la mort | *Splitter* (se divise en 2 petits zombies) |

`/lethalspecial <type> [count]` pour en faire apparaître. Config globale : `specialEnabled`,
`specialBaseChance`, `specialPhaseScale`, `specialMaxChance`, `specialShowName`, `specialActionInterval`.
**Tout est configurable, aucune valeur en dur.** Par type : `special<Type>Phase` (phase de déblocage, `0` =
toujours), `special<Type>Weight` (poids de tirage, `0` = jamais), **et chaque magnitude de comportement** —
puissance/portée/fusible du Bombeur, rayons du Hurleur/Soigneur, nombre d'invocations du Nécromancien,
enfants/taille du Splitter, vitesse du Sprinteur, bond du Bondisseur, taille/PV/résistance/armure du
Juggernaut. Les défauts reproduisent la table d'origine, donc ne rien toucher ne change rien.

### 📈 Difficulty phases — endless escalation
A **server-global phase** starts at 1 and auto-advances on a ~10-minute jittered timer. It is **monotonic
(only ever rises)** and **persists across sessions** — it never resets. By default it is **unbounded** and
keeps climbing forever; the per-phase stat curve is a saturating function tuned to match the old hand-built
table near phase 15 but continue cleanly toward infinity (see `phase/PhaseConfig.java`). Each advance is
announced in chat as **`☠ Phase N`** with a cyclic tier colour.

Higher phase → each spawned zombie rolls more HP / damage / speed from **widening** random ranges, wears
better and more **enchanted** gear (a random tool/weapon type — sword/axe/pickaxe/shovel/hoe — plus armor,
material tier rising leather→netherite), and gets more/stronger effects. Gear has a small per-item drop
chance. Optional cap: set `phaseMaxEnabled=true` to stop climbing at `phaseMax` (default 50) — then either
**loop** back to phase 1 or **pin** at the ceiling (`phaseLoop`).

`/lethalphase [n]` shows or forces the phase (a manual force ignores the cap). Config lives in
`config/domain/ProgressionConfig.java` (`phaseSystemEnabled`, `phaseIntervalTicks`, `phaseJitterTicks`,
`phaseMaxEnabled`, `phaseMax`, `phaseLoop`, gear/effect decay curves).

### ✨ Random effects — zombie "types"
~25% of spawned zombies carry one **random beneficial** effect for their whole life (infinite duration,
random level I–III), rolled UUID-seeded in `finalizeSpawn`. The pool is everything useful to a predator:
Speed, Strength, Resistance, Regeneration, Jump Boost, Haste (digs faster), Health Boost, Absorption —
plus a **custom zombie-only `LEAP` effect** (no Fire Resistance: every zombie must burn in daylight; a
registered Holder-based MobEffect that shows particles only). Vanilla **Jump Boost** makes a zombie jump
*higher* (folded into the vertical impulse); the custom **LEAP** makes it lunge *farther* (folded into the
horizontal leap) — both dynamic, read live, never hard-coded. Config: `randomEffectChance`,
`randomEffectMaxAmplifier`, `leapEffectPerLevel`.

### ⚡ GPU compute
The per-dimension flow field is solved off the server thread. When an OpenCL GPU is present it is used
automatically (`useGpu=true` default; logs `GPU: <device> — OpenCL OK`); otherwise the **multi-core** CPU
solver runs transparently — one flow field is solved across `cores-2` threads with a parallel Bellman-Ford
relaxation (the same algorithm as the GPU kernel), not a single-core Dijkstra. Any GPU error degrades to
the CPU path — the GPU is never load-bearing (`GPU: unavailable — CPU fallback` in the log). Config:
`flowCpuThreads` (0 = auto cores-2).

### 🖥️ Client rendering
Sodium/Iris-aware client config with a distance-cull mixin for zombies.

---

## 🛠 Build & run

The Gradle wrapper works the same on every OS — use `./gradlew` on Linux/macOS or `.\gradlew.bat` on Windows:

```bash
# Build the obfuscated player jar (build/libs/) — ProGuard runs as a post-remap step
./gradlew build

# Launch the dev client (loads run/mods/, e.g. Sodium + Iris + perf mods)
./gradlew runClient

# Launch a headless dev server (also runs the /lethaldev test harnesses)
./gradlew runServer
```

There are two jars: `./gradlew build` (or `scripts/build-player.bat`) ships the **player** jar (obfuscated
via `proguard-rules.pro`); `scripts/build-dev.bat` packages main + the `src/dev` source set (test harnesses
and the `/lethaldev` & `/lethalspawn` commands) as the **developer** jar. The `scripts/*.bat` helpers are
Windows conveniences; on other platforms call the Gradle tasks directly.

<details>
<summary><strong>⚠️ <code>runClient</code> remap crash (<code>ClosedFileSystemException</code>)</strong></summary>

<br>

Fabric Loom's dev launch intermittently fails to remap the dependency mods (a known Loom bug, not a mod
bug). When it recurs, do a clean relaunch — kill stray game JVMs, wipe the runtime remap cache, then a
single `runClient`:

```bash
# Linux/macOS
pkill -f 'fabric.*devlaunch' ; rm -rf run/.fabric ; ./gradlew runClient
```
```powershell
# Windows
Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
Remove-Item -Recurse -Force "run\.fabric" -ErrorAction SilentlyContinue
.\gradlew.bat runClient
```

See the `fix-gradle-runclient-remap-error` skill for the full escalation order.

</details>

---

## ⚙️ Config

- **Server** defaults live in the `config` package: the `LethalBreedConfig` facade over per-topic holders in
  `config/domain/` (climb, water, targeting, LOD, block-op budgets, GPU, spawn control, variation,
  progression, contamination, mood). Fields are exposed to `/lethalconfig` and the config screen by
  reflection (`ConfigSchema` scans the holders' declared fields). Key flags: `pillarJumpPower`,
  `pillarMaxHeight`, `floatInWater`, `waterSwimSpeed`, `waterDiveSpeed`, `targetDetectRadius`, `useGpu`.
- **Full custom — nothing hard-coded.** Every gameplay magnitude is a config field (special-zombie
  behaviour, phase curves, swim/leap/pillar/descend tuning, contamination, mood…). Even the low-level
  numeric constants (math tolerances, safety clamps, the vanilla mob-cap divisor) are exposed via
  `ExpertConfig` under a dedicated **Expert** tab — changing those can break movement/spawn/plague
  correctness, so they are kept out of the normal gameplay tabs. Defaults reproduce the original behaviour
  exactly.
- **Client** optimizations: `config/lethalbreed-client.json` — cull distance, max rendered zombies, Sodium
  adaptation.
- **Dev**: `devClimbTest=true` (in `ProgressionConfig`) builds a headless wall+target+zombies arena on
  server start and logs `[ClimbDbg]` (run `runServer` to watch climb behaviour without a client). Turn it
  back off before shipping.

---

## 🎮 Commands

| Command | Source set | Purpose |
|---------|:----------:|---------|
| `/lethalconfig` | main | view/set config fields at runtime |
| `/lethalphase [n]` | main | show or force the difficulty phase |
| `/lethalspecial <type> [count]` | main | spawn a specific special variant |
| `/lethaldev …` | dev | headless test harness controls |
| `/lethalspawn …` | dev | spawn helpers for testing |

---

## 📂 Project layout

<details>
<summary>Expand package tree</summary>

```
src/main/java/com/dreykaoas/lethalbreed/
├── LethalBreedMod.java     # entry point: events, spawn handling, GPU warm-up (client: client/LethalBreedClient)
├── entity/                 # SmartZombie + brain, registry, variation, spawn control
│   ├── mood/               # celebrate / flee / distress / regen
│   └── move/               # pillar-up (Ascent), descend, water swim/dive + dispatch
├── ai/  ai/flowfield/      # flow field, LOD; flowfield/gpu = OpenCL/JOCL compute manager + kernel
├── block/                  # break/build coordinators, op queue, placed-block tracker
├── sound/  spatial/  tick/ # sound bus, spatial grid, staggered LOD scheduler
├── effect/                 # custom LEAP MobEffect; effect/contamination = Super Contamination plague
├── phase/                  # endless phase escalation: manager, saturating stat curve, gear equipper
├── special/                # 8 special variants: type, roller, runtime behavior
├── command/                # /lethalconfig, /lethalphase, /lethalspecial
├── config/  config/domain/ # config facade + per-topic holders (reflection-scanned)
├── client/  client/screen/ # Sodium/Iris-aware client config + config screen
├── dimension/ net/ init/   # per-dimension AI context, networking, registration
└── mixin/  mixin/client/   # finalizeSpawn (size), float-in-water, goal accessor/suppress, client render hooks
```

</details>

---

## ✅ In-game test

1. `./gradlew runClient`, flat Creative world.
2. Spawn zombies beyond vanilla aggro range → they stream toward you, break a glass wall, bridge a pit.
3. Stand on a tower/ledge → they pillar dirt up to reach you, facing you, no levitation.
4. Lure them into water and dive → they float at the surface, dive after you when you're below, and break
   through underwater obstacles.
5. No baby/drowned spawns; zombies are unarmored; sizes vary with no spawn-time resize.

<div align="center">

---

<sub>☠ Built for servers that want their zombies to actually hunt.</sub>

</div>
