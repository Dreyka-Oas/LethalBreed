---
name: zombie-target-short-term-memory
description: Use when a LethalBreed zombie should remember a target's LAST KNOWN position after losing it (out of sight AND out of hearing) and keep heading there briefly, instead of forgetting the instant it can't sense it — while still switching to any nearer/visible/heard entity that shows up. Covers the memory fields, the LODManager fallback, and how it interacts with sight-or-hearing detection.
---

# Zombie target short-term memory (last-known position)

## Goal

Detection is sight-OR-hearing (`zombie-targeting-nearest-and-vision-los`). When BOTH are lost — the target
left hearing range (`soundBaseRadius`) and is behind an opaque wall — the zombie should not blank out: it
heads to where the target **last was** for a few seconds, then gives up. A live detection (any seen/heard
entity, nearest wins) always overrides the memory.

## How

`SmartZombie` holds the memory: `memX/memY/memZ`, `memoryExpire` (game-tick), `memory` flag.
- `rememberTarget(x,y,z, expireTick)` — refresh the spot + expiry; called **every tick a live target is
  detected**, so memory tracks the target's current position right up until it's lost.
- `setMemoryTarget()` — pursue the remembered point: `targetEntity = null` (no live entity → no melee),
  `tgt* = mem*`, `hasTarget = true`.
- `distanceToTargetSq()` — squared distance to the current target point (live or remembered).

`LODManager.classify` (runs every activation, even for FROZEN zombies):
1. `findNearest` returns an entity → `setTarget` + `rememberTarget(now + targetMemoryTicks)` + set vanilla
   target + LOD from distance. **Live detection overrides memory** (nearest detected always wins).
2. Else if `hasMemory() && now < memoryExpire()` → `setMemoryTarget()`, clear vanilla target; if within
   `soundArriveDistance` of the spot (reached it, nothing there) → forget (`clearTarget/clearMemory`,
   FROZEN); else LOD from distance so it keeps pursuing/digging toward the spot.
3. Else → `clearTarget` + `clearMemory`, FROZEN.

Config: `targetMemoryTicks` (default 200 = 10s; 0 disables).

## Key facts

- **Memory only kicks in when truly undetected.** A close hidden entity is HEARD every tick (within
  `soundBaseRadius`), so it's a live target continuously and never enters the memory branch — memory is the
  fallback for an entity that fully escaped (out of hearing + no LOS).
- **During memory there is no live entity** (`targetEntity == null`): no melee, vanilla target cleared. The
  zombie navigates/climbs/descends/digs toward the remembered point via the normal `tick()` path (which
  reads `tgtX/Y/Z`), then forgets on arrival-with-nothing or on expiry.
- **Keep the LOD non-FROZEN while memory is active** so the scheduler keeps ticking the zombie; only go
  FROZEN once memory expires/clears (same as a normal no-target zombie).
- `level.getGameTime()` is the clock — store an absolute expiry tick, don't decrement a counter (classify
  cadence is per-activation, not per-tick).

## Don't regress

- Live detection must always win over memory (check `findNearest` first).
- Refresh memory every detected tick (so the remembered point is the *last* seen/heard position, not the
  first).
- Forget on arrival or expiry — don't let a zombie stand on an empty last-known spot forever.
