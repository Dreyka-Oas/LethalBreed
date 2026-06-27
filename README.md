# LethalBreed

A Fabric mod for **Minecraft 1.21.11** that turns vanilla zombies into a relentless, environment-aware
threat. They navigate with a per-dimension flow field, **pillar up** to reach perched targets, **descend**
(carve or build a staircase) to targets below, **break and bridge** terrain in the way, **see and hear**
(through real noise, not walls), **swim and dive** through water, **remember** a target's last-known spot,
and hunt the nearest living entity — all built to scale toward ~1000 active zombies. Heavy pathfinding runs
off the server thread, with an optional **AMD GPU (OpenCL)** compute path and a multithreaded CPU fallback.

See [`PLAN.md`](PLAN.md) for the full design and phase breakdown, and [`.skills/`](.skills/) for hard-won
gotchas (each documents a problem + the fix).

## Toolchain

| Thing | Value |
|-------|-------|
| Minecraft | 1.21.11 (Mojang mappings) |
| Java | 21 — **BellSoft Liberica NIK 23** (GraalVM JIT) |
| Loader / API | fabric-loader `0.19.3` / fabric-api `0.141.4+1.21.11` |
| Loom / Gradle | `1.17.12` / `9.5.1` (wrapper) |
| GPU compute | any AMD GPU via OpenCL/JOCL (auto-used when present; CPU fallback) |

The build JDK is pinned in `gradle.properties` via `org.gradle.java.home` — update that path if the JDK
moves.

## Features

### Vertical pursuit — jump-and-place pillar
A zombie whose target is perched close above and out of reach **pillars up like a player**: it jumps
(real upward velocity impulse), drops a dirt support into the cell it left, lands one block higher, and
repeats until it reaches the target (or hits `pillarMaxHeight`). It faces the target while climbing. The
column is auto-removed by the placed-block tracker. Triggers on sight of an overhead target — no need to
be provoked. (Notably: `setJumping`/`setPos` do **not** work for this from the tick scheduler — see the
`zombie-ascend-jump-and-place-not-setpos` skill.) A live **Jump Boost** effect is folded into the impulse
dynamically (`+0.1 × (amplifier+1)`, the vanilla rule) — not hard-coded — so a boosted zombie jumps higher.

### Vertical pursuit — descend to targets below
The mirror of the pillar: a zombie whose target is below comes **down** to it at any depth, choosing the
safe option layer by layer — walk short drops, **carve a staircase** through solid terrain toward the
target, take a safe straight-down shaft, or **build a descending dirt staircase** out over open air (place
first, wait for the block to exist, then step — toward the target's side, never the wrong way). It never
breaks the last block over a deep void, so it won't plummet. See `zombie-descend-carve-and-build-staircase`.

### Water — float, dive, break
Zombies can't drown, so in water they:
- **surface gently** and hold at the top (no fast pop, no dirt towers),
- **dive** after a target that is itself submerged below them,
- **swim straight** at the target (driven directly, facing it — no nav-induced spinning), and
- **break** solid blocks between them and the target (and the floor below when diving).

Added via a vanilla `FloatGoal` plus a per-tick swim pass. Config: `floatInWater`, `waterRiseSpeed`,
`waterDiveSpeed`, `waterSwimSpeed`.

### Targeting — nearest detected, sight or noise, with memory
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

### Block ops, sound, LOD
Reactive break/bridge with a per-tick budget; placed dirt auto-removed after `placedBlockLifetimeTicks`
(no drop). Player footsteps (movement-gated, sneaking is silent) and block-breaks emit sound events that
attract zombies. A 4-tier LOD (HIGH/MEDIUM/LOW/FROZEN) throttles distant zombies.

### Spawn control & variation
Baby zombies and drowned are discarded; equipment is stripped (config-gated). Each zombie gets a small,
UUID-deterministic size/speed/damage/leap variation — applied in `Zombie.finalizeSpawn` (**before** the
client sees the entity) so there is no visible resize on spawn.

### GPU compute
The per-dimension flow field is solved off the server thread. When an OpenCL GPU is present it is used
automatically (`useGpu=true` default; logs `GPU: <device> — OpenCL OK`), otherwise the CPU solver runs
transparently. Any GPU error degrades to CPU — it is never load-bearing.

### Client rendering
Sodium/Iris-aware client config with a distance-cull mixin for zombies.

## Build & run

```powershell
# Build the mod jar (build/libs/)
.\gradlew.bat build

# Launch the dev client (loads run/mods/, e.g. Sodium + Iris + perf mods)
.\gradlew.bat runClient

# Launch a dev server (headless)
.\gradlew.bat runServer
```

> **Bash is broken on this dev box (fork errors) — run Gradle via PowerShell.**

### `runClient` remap crash (`ClosedFileSystemException`)

Fabric Loom's dev launch intermittently fails to remap the dependency mods (a known Loom bug, not a mod
bug). When it recurs, do a clean relaunch — kill stray game JVMs, wipe the runtime remap cache, then a
single `runClient` with no Gradle task in between:

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
Remove-Item -Recurse -Force "run\.fabric" -ErrorAction SilentlyContinue
.\gradlew.bat runClient
```

See the `fix-gradle-runclient-remap-error` skill for the full escalation order.

## Config

- Server defaults live in `LethalBreedConfig` (climb, water, targeting, LOD, block-op budgets, GPU, spawn
  control, variation). Key flags: `pillarJumpPower`, `pillarMaxHeight`, `floatInWater`, `waterSwimSpeed`,
  `waterDiveSpeed`, `targetDetectRadius`, `useGpu`, `debugLogInterval` (0 = no perf-recap spam).
- Client optimizations: `config/lethalbreed-client.json` — cull distance, max rendered zombies, Sodium
  adaptation.
- Dev: `devClimbTest=true` builds a headless wall+target+zombies arena on server start and logs
  `[ClimbDbg]` (run `runServer` to watch climb behaviour without a client). Turn it back off before
  shipping.

## Project layout

```
src/main/java/com/dreykaoas/lethalbreed/
├── LethalBreedMod.java        # entry point: events, spawn handling, GPU warm-up
├── entity/                    # SmartZombie (climb/water/pursuit), registry, variation, spawn control
├── ai/ flowfield/ goals/      # flow field, LOD, target selection
├── block/                     # break/build coordinators, op queue, placed-block tracker
├── sound/  spatial/  tick/    # sound bus, spatial grid, staggered scheduler (climbers/swimmers passes)
├── gpu/                       # OpenCL/JOCL compute manager + kernel dispatch
├── mixin/                     # finalizeSpawn (size), float-in-water, goal accessor/suppress
└── config/                    # LethalBreedConfig
```

## In-game test

1. `.\gradlew.bat runClient`, flat Creative world.
2. Spawn zombies beyond vanilla aggro range → they stream toward you, break a glass wall, bridge a pit.
3. Stand on a tower/ledge → they pillar dirt up to reach you, facing you, no levitation.
4. Lure them into water and dive → they float at the surface, dive after you when you're below, and break
   through underwater obstacles.
5. No baby/drowned spawns; zombies are unarmored; sizes vary with no spawn-time resize.
