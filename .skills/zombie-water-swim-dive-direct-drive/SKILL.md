---
name: zombie-water-swim-dive-direct-drive
description: Use when LethalBreed zombies in water misbehave — pillar/place blocks while floating, rise out too fast, spin in circles, or rocket "anywhere". Water movement is driven directly (velocity + facing) on an every-tick swim pass, NOT via the bucketed tick or the path navigation.
---

# Zombie water movement — direct velocity drive on an every-tick pass

## Symptoms each fix addresses

- Placing dirt / pillaring while in water → nonsense.
- "Pops" out of the water too fast.
- Spins in circles underwater.
- Swims very fast / "goes anywhere".
- Stays at the surface instead of diving to a submerged target.

## Design

Zombies can't drown. In water:
- Default: surface gently and hold at the top (FloatGoal handles idle floating; we override the speed).
- If the target is itself in water BELOW → dive after it.
- Swim straight at the target, facing it; break solid blocks in the way.

### Key facts (each was a bug first)

1. **Never run water vertical control on the bucketed `tick()`** — it runs ~1-in-5 ticks, so the per-tick
   `FloatGoal` lift out-pushes a sparse dive impulse and the zombie never descends. Drive it from an
   **every-tick `swimmers` pass** in `TickScheduler` (mirror the `climbers` pass): `tick()` only sets a
   `swimming` flag and returns (so no block ops happen in water); `swimStep()` does the real work each tick.
2. **Don't use path navigation to move in water** — the water pathfinder fails to settle and the zombie
   spins. `entity.getNavigation().stop()`, then set yaw/`yBodyRot`/`yHeadRot` toward the target and push
   velocity at it directly.
3. **Blend velocity, don't hard-set it** — a fixed `setDeltaMovement` each tick glides at constant speed
   with no decel → overshoot / "anywhere". Use `nvx = v.x*0.6 + desired*0.4`, modest `waterSwimSpeed`
   (~0.06), and zero the drive within ~0.6 blocks of the target.
4. **Use the target's LIVE position** (`targetEntity.getX/Y/Z()`), not the bucket-cached `tgt*` — the
   cached point is stale and looks like swimming the wrong way.
5. **Surface gently**: only a small upward `waterRiseSpeed` (~0.04) while `isUnderWater()`, 0 at the top —
   not the FloatGoal's fast pop (which our every-tick y-override replaces).
6. **Break underwater**: water isn't solid (`blocksMotion()` false), so breaking toward the target only
   hits real obstacles; when diving also open the floor cell below.

Config: `floatInWater`, `waterRiseSpeed`, `waterDiveSpeed`, `waterSwimSpeed`.

## Don't regress

- No pillar/block-place in water (cancel `pillaring`, return early in `tick()`).
- Water vertical/horizontal control must stay on the every-tick swim pass, never the bucket cadence.
- Drive movement directly (stop nav) and face the target to avoid spinning.
