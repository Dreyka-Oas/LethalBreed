# LethalBreed — What changed & how

A running record of the behaviour work, with the *how* and the gotchas that bit us (each gotcha is also a
skill in [`.skills/`](.skills/) so the same mistake isn't repeated).

## Summary of this work block

| Area | What it does now | How |
|------|------------------|-----|
| **Spawn size** | No visible grow/shrink on spawn | Apply the random `SCALE` in `Zombie.finalizeSpawn` (before the client sees the entity), not in `ENTITY_LOAD` (post-sync). |
| **GPU compute** | Auto-uses the AMD GPU when present | `useGpu=true` default; warm + log it on server start; CPU fallback when no OpenCL GPU. |
| **Climb** | Pillars up to perched targets like a player | Jump = upward **velocity impulse**, place a dirt support in the cell left, land one higher, face target; trigger off-sight (no `stuck` gate). |
| **Descend** | Comes down to targets below — any depth | Direct `targetUnderfoot` trigger (mirror of climb); layered `descendStep`: walk short drops → carve a staircase through solid terrain → safe straight-down shaft → BUILD a descending dirt staircase over open air. Never breaks the last block over a deep void. |
| **Water** | Float / dive / swim / break, no spinning | Every-tick swim pass: direct velocity + facing (no nav), gentle rise, dive when target below, break solid obstacles; never pillar in water. |
| **Targeting** | Always hunts the nearest entity | Strip vanilla target goals so our nearest-pick is authoritative; set vanilla target each tick. |
| **Vision** | Sees through glass, blind through stone | LOS acquisition: block sight only on `canOcclude()` (opaque); hidden targets handled by sound. |
| **Sound** | Heard through walls, but only real noise | Radius-based, no wall muffling — BUT a candidate is "heard" only when actually emitting noise this tick (walking-not-sneaking / acting / hurt), so a motionless silent entity is sight-only (no more seeing through walls). |
| **Logs** | No console spam | `debugLogInterval=0` (perf recap off by default). |

## Detail & the *how*

### Spawn size — apply before client sync
The per-zombie size variation was applied in `ServerEntityEvents.ENTITY_LOAD`, which fires *after* the
entity is tracked to the client → the client interpolated the size change = a visible resize. Moved it to
a `Zombie.finalizeSpawn` mixin (runs before `addFreshEntity`). `MOB_FINALIZE_SPAWN` does not exist in this
fabric-api version, so a mixin is required. → skill `apply-entity-attributes-before-client-sync`.

### GPU auto-use
`useGpu` flipped to `true`; `GpuFlowField` already routes to the GPU when available with a CPU fallback.
Boot logs `GPU: <device> — OpenCL OK` (verified: `gfx1200` = RX 9060 XT).

### Climb — jump-and-place pillar
Replaced the old `setPos` "spider climb" (looked like flying) with a real jump-and-place pillar. The hard
part: making the jump actually happen from the scheduler.
- `setJumping(true)` from END_SERVER_TICK is a **no-op** (mob stays grounded).
- A per-tick `setPos` recenter **resets `onGround`** and kills the jump.
- A direct upward **`setDeltaMovement` impulse** works (survives into the next tick's `travel()`).
- Trigger climbing the moment the target is perched above and unreachable — **not** on the `stuck`
  heuristic (a jittering zombie rarely registers stuck, so it only climbed after being hit).
- Face the target while climbing (`yRot`/`yBodyRot`/`yHeadRot`).
→ skills `zombie-ascend-jump-and-place-not-setpos`, `zombie-climb-and-float-working-solution`,
`entity-velocity-not-applying-use-setpos`.

### Water — float / dive / swim / break
Driven on an **every-tick** swim pass (`swimmers` set in `TickScheduler`, mirroring `climbers`) because the
bucketed `tick()` (1-in-5) let the per-tick `FloatGoal` out-push a sparse dive impulse. In water: stop nav,
face target, blend velocity toward it (modest speed, decel near target, live target position), gentle rise
while submerged, dive when the target is in water below, break solid obstacles in the way. Never pillar in
water. → skills `zombie-water-swim-dive-direct-drive`, `zombies-float-in-water-add-floatgoal`.

### Targeting + vision
Vanilla target goals re-locked the player every tick, so the zombie wouldn't switch to a closer entity —
stripped them (`ZombieTargetSuppressMixin`) so our nearest pick wins. Vision acquisition gates on an
opaque-only line of sight (`canOcclude()` voxel walk) so glass/ice/leaves are see-through but stone blocks
sight; hidden targets fall back to sound. Hearing now requires real **noise** — `TargetSelector.isAudible`
gates the radius on the entity actually emitting sound this tick (walking ≥ `soundMoveThreshold` and not
sneaking, or arm-swing/using-item, or `hurtTime > 0`), mirroring the `SoundEventBus` footstep rule. A
motionless silent entity (e.g. a trapped villager) is no longer "heard" through walls — it's sight-only.
→ skill `zombie-targeting-nearest-and-vision-los`.

## Build / run gotchas (this dev box)

- **Bash is broken** (fork errors) — run Gradle via PowerShell. → skill `use-powershell-not-bash`.
- **`runClient` `ClosedFileSystemException`** (Loom remap flake, intermittent) — kill stray game JVMs, wipe
  `run\.fabric`, then a single `runClient` with no Gradle task in between. → skill
  `fix-gradle-runclient-remap-error`.
- **Always run the game after a mixin change** — mixin *apply* errors (e.g. `@Shadow` of an inherited
  field) compile green and only fail at class-load. → skill `mixin-shadow-inherited-field-use-accessor`.
- **Diagnose AI with the headless arena** — `devClimbTest=true` + `runServer` + `[ClimbDbg]` logs found
  every climb/water bug that guessing missed. Turn it back off before shipping. → skill
  `headless-ai-test-harness`.

## Skills index (`.skills/`)

- `apply-entity-attributes-before-client-sync` — set client-rendered attributes in `finalizeSpawn`.
- `zombie-ascend-jump-and-place-not-setpos` — climb via velocity impulse, not setPos/setJumping.
- `zombie-descend-carve-and-build-staircase` — come down: carve/shaft/build-staircase, never plummet.
- `zombie-jump-dynamic-jump-boost-effect` — fold live Jump Boost into the jump impulse, not hard-coded.
- `zombie-target-short-term-memory` — remember last-known target spot after losing sight+sound.
- `zombie-climb-and-float-working-solution` — the verified climb+float reference.
- `zombie-water-swim-dive-direct-drive` — water movement on the every-tick swim pass.
- `zombies-float-in-water-add-floatgoal` — add `FloatGoal` via accessor.
- `zombie-targeting-nearest-and-vision-los` — nearest targeting + opaque-only vision.
- `mixin-shadow-inherited-field-use-accessor` — `@Accessor` for inherited fields.
- `entity-velocity-not-applying-use-setpos` — the deltaMovement-at-END_SERVER_TICK pitfall.
- `fix-gradle-runclient-remap-error` — the Loom remap flake recovery.
- `use-powershell-not-bash` — Bash is broken here.
- `headless-ai-test-harness` — the ClimbTest arena diagnostic.
