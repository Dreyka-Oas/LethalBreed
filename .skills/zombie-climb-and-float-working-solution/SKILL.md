---
name: zombie-climb-and-float-working-solution
description: Reference for the WORKING LethalBreed zombie vertical-pursuit + water behaviour. Zombies pillar up to elevated targets by real jump-and-place (impulse jump, place support, land higher, face the target) and float at the water surface via FloatGoal. Read this before touching pillar/climb or water movement so you don't regress the hard-won fixes.
---

# Zombie climb + float — the working solution (verified in-world)

This documents the behaviour that is CONFIRMED working, so future edits don't re-break it. Two features:
zombies climb to perched targets, and zombies float in water.

## 1. Climb — jump-and-place pillar (`SmartZombie.pillarStep`)

A zombie that has an elevated, unreachable target builds a dirt column up to it, like a player pillaring.

How it actually works (each fact was a failed attempt before it worked — see
`zombie-ascend-jump-and-place-not-setpos` for the negatives):

- **Trigger without `stuck`.** In `tick()`, the moment the target is `dy >= climbThreshold` above and
  within `climbHorizRadius` horizontally and unreachable (`!canHit`), call `initiatePillar`. Do NOT gate
  on the horizontal "stuck" heuristic — a zombie jittering at a wall rarely registers stuck, so gating made
  it climb only after the player hit it.
- **Jump = upward velocity impulse, NOT `setJumping`.** From the scheduler's per-tick climber pass
  (END_SERVER_TICK), `setJumping(true)` is a silent no-op (mob stays grounded). Instead:
  `entity.setDeltaMovement(0, pillarJumpPower /*0.42*/, 0); entity.hurtMarked = true;`. This survives into
  the next tick's `travel()` and lifts the zombie ~1.1 blocks.
- **No `setPos`.** A `setPos` recenter resets `onGround` and kills the next jump. Don't.
- **Place the support in the cell the feet just left** (`pillarStandY`), only once airborne above it
  (`getY() >= pillarStandY + 1.0`). Lands one block higher → `risen` ratchets 1→N.
- **Face the target while climbing**: set `yRot`/`yBodyRot`/`yHeadRot` from `atan2(hz, hx)` each tick, so
  the zombie looks where it jumps instead of staring sideways.
- `tick()` early-returns on `pillaring` so vanilla nav/leap don't fight it. The block queue drains AFTER
  the climber pass in the same tick, so the support exists before next tick's gravity check.

Config: `pillarJumpPower` (0.42), `pillarMaxHeight` (24), `climbThreshold`, `climbHorizRadius`,
`climbGiveUpCooldown`.

## 2. Float in water (`ZombieFloatInWaterMixin` + `MobGoalsAccessor`)

Vanilla zombies omit the `FloatGoal` every other land mob has — that omission is why they sink and walk
the bottom. Add it back: inject at TAIL of `Zombie.registerGoals`, get the inherited `goalSelector` via an
`@Accessor` on `Mob` (a `@Shadow` from a `Zombie` target fails — see
`mixin-shadow-inherited-field-use-accessor`), and `addGoal(0, new FloatGoal(self))`. Config: `floatInWater`.

## How it was diagnosed (reuse this method)

Guesswork failed repeatedly. What worked: the **headless ClimbTest arena**.
- Set `LethalBreedConfig.devClimbTest = true`, run `gradlew runServer` (no client needed), watch the
  `[ClimbDbg]` lines (`y, tgtY, horiz, dy, stuck, climb/PILLAR, risen, ground`).
- `risen=0` + `ground` flickering exposed the `setPos`/`setJumping` failures instantly; `risen` ratcheting
  1→11 and ending at the villager's Y proved the impulse fix.
- Always turn `devClimbTest` back to `false` and rebuild before shipping.
- Bash is broken on this box — drive gradle via PowerShell; relaunch after a build needs the
  `run\.fabric` wipe (see `fix-gradle-runclient-remap-error`).

## Don't regress

- Never reintroduce `setPos` vertical lift (looks like flying) or `setJumping` from the scheduler (no-op).
- Keep the climb trigger off the `stuck` gate.
- Keep `floatInWater` adding `FloatGoal` via the accessor, not a `@Shadow`.
