---
name: zombie-descend-carve-and-build-staircase
description: Use when LethalBreed zombies won't come DOWN to a target below them — they stand at a ledge/roof and stare, or stop the moment there's only a thin floor over a void. Covers the descend trigger (mirror of the climb trigger, no "stuck" wait) and the layered descendStep: walk short drops, carve a staircase through solid terrain, dig a safe straight-down shaft, and BUILD a descending dirt staircase out over open air.
---

# Zombie descent — carve down, and build a staircase over a void

## Why it broke

Descent was gated on the `stuck` heuristic AND the straight-down dig required a solid block exactly
1 below. So a zombie standing above its target just stood there (a jittering zombie rarely registers
`stuck`), and any drop deeper than 1 safe block stalled. The vanilla pathfinder **refuses to walk off any
drop taller than its fall limit**, so without us driving the descent the zombie never comes down.

## The two fixes

### 1) Trigger descent directly — symmetric to the climb
In `SmartZombie.tick()`, alongside the `targetOverhead` climb trigger, add `targetUnderfoot`:
`dy <= -descendThreshold && horizSq <= climbHorizRadius²`. When true → call `descendStep` **immediately**,
no `stuck` wait. (Keep the old `stuck`-based descend for lateral cases where the target is below *and* far.)

### 2) `descendStep` — layered, safest-first, never strands
Ordered so it always prefers the cheapest safe descent and only builds as a last resort:

0. **Short safe drop nearby** (`tryWalkableStepDown`, ≤ `safeDropBlocks`) → just step/drop off, no digging.
1. **Forward staircase through solid terrain**: the cell one step toward the target has a solid floor
   (`(ax,by-2)` solid) → break any breakable head/feet block in the way, then walk to `(ax,by-1)`. This is a
   real descending staircase carved through a hillside/underground, one safe step per activation.
2. **Straight-down shaft**: break the block under our feet, but **only when a solid landing sits within
   `safeDropBlocks` below it** (`fallDistanceInto(level, bx, by-1, bz, safeDropBlocks) <= safeDropBlocks`).
   Zombie drops a short safe distance onto the new floor and repeats → self-dug shaft to ANY depth, no fall
   damage. `getNavigation().stop()` first so the pathfinder doesn't drag it off the column.
3. **BUILD a descending staircase** (the "1 block of floor over a pit" case — straight-down is unsafe because
   the void below is deeper than `safeDropBlocks`): lay a support at `(ax, by-2)` — one level
   down-and-forward toward the target — then step onto it. Repeats into a safe diagonal stair down, even out
   over open air (the **inverse of the pillar-up**). Placed dirt auto-removes (`placedBlockLifetimeTicks`).

### Build the stair TOWARD the target, not straight down (direction bug)
The build MUST head in the target's horizontal direction. Original code only built straight-down (or a
synthesized axis) *after* the straight-down branch ran — so a zombie with a target **below and to one side**
dug a shaft in place / built the wrong way ("places the descend block only one way while it wants to go the
other side"). Fix: when the target is horizontally offset (`sdx|sdz != 0`) and the cell toward it is a deep
void, do the place-first/step build at `(bx+sdx, *, bz+sdz)` **inside the forward branch**, before the
straight-down carve. Straight-down (and the synthesized-axis build) is now only the fallback for a target
**directly below** (`sdx==sdz==0`) or a forward path walled off by unbreakable blocks.

## Key facts (each was a bug)

- **Build the step FIRST, step onto it NEXT activation — never the same one.** The block-op queue drains
  *after* the tick, so if you `enqueuePlace(step)` and `nav.moveTo(step)` in the same activation, the zombie
  walks into thin air before the support exists and just falls ("it placed nothing where it went to
  descend"). Lay the block, `getNavigation().stop()`, `return`; only move once that cell reads solid. Same
  place-then-stand discipline as the pillar-up.
- **Don't break the last block over a deep void** — breaking it = the zombie plummets the full depth = fall
  damage / death. The safe-landing check (`fall <= safeDropBlocks`) is what makes the straight-down dig safe;
  keep it. When it fails, fall through to the **build-staircase**, do NOT just dig anyway.
- **The vanilla pathfinder will not take a tall drop.** A bare `nav.moveTo(x, by-1, z)` at the lip of a deep
  cliff finds no path and the zombie freezes — you must carve/build the descent yourself.
- **`fallDistanceInto(level, x, y, z, max)`** scans from `y-1` downward; to measure the fall *after* removing
  the block at `by-1`, pass `y = by-1` (it then scans from `by-2`).
- Descent runs on the **bucketed `tick()`** (not an every-tick pass like climb/swim): block breaking is async
  via `BreakManager` and survives between bucket activations (`breakGraceTicks` 10 > bucket gap 5), so no
  scheduler change is needed. (Climb needs every-tick only because of jump *physics*.)

## Don't regress

- Keep both descend triggers: `targetUnderfoot` (direct) AND the `stuck` lateral path.
- Keep the safest-first order (0 walk → 1 carve-stair → 2 straight shaft → 3 build-stair). Building is the
  last resort; never build when a walk/carve descent is available (that's what made the old "absurd dirt
  arches").
- The straight-down dig must stay gated on a safe landing within `safeDropBlocks`. Never remove that guard.
- Related: ascent is the mirror image — see `zombie-ascend-jump-and-place-not-setpos` and
  `zombie-climb-and-float-working-solution`.
