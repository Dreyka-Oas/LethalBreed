---
name: entity-velocity-not-applying-use-setpos
description: Use when a mob in LethalBreed won't move the way you set it (e.g. a wall-climb, levitate, custom dash) even though you call setDeltaMovement every tick — the velocity gets cancelled. Lift/move via setPos instead.
---

# Fix: setDeltaMovement from END_SERVER_TICK doesn't move the mob — use setPos

## Symptom

You set a velocity on a zombie every tick (e.g. `entity.setDeltaMovement(x, climbSpeed, z)`) from the
mod's scheduler, but the mob doesn't actually rise/move. In LethalBreed this showed up as zombies that
reached a wall base (`horiz<2`, `stuck=true`, target overhead) but never climbed — `ground` stayed
`true`, the climb started and immediately stopped, repeat.

## Cause

LethalBreed's scheduler runs at `ServerTickEvents.END_SERVER_TICK` — AFTER every entity has ticked.
The velocity you set there is the entity's velocity at the START of the next tick, but the mob's own
movement tick (`Mob.aiStep` → `MoveControl` + gravity, then `travel`) overwrites/cancels it before it
applies. So a `deltaMovement.y` you set never raises the mob.

## Fix

Drive the position **directly** with `setPos` each tick — vanilla movement can't cancel a hard position
set:

```java
// rise straight up; leave X/Z to physics (so knockback / shoving still works), gentle press toward target
entity.setPos(entity.getX(), entity.getY() + climbSpeed, entity.getZ());
entity.setDeltaMovement(px, 0.0, pz); // horizontal only
```

- Only override the axis you control (Y for a climb). Leaving X/Z physics-driven keeps the mob shovable.
- `setPos` skips collision, so guard against clipping (e.g. stop if the block above the head
  `blocksMotion()`), and stamp `entity.resetFallDistance()` while lifting so it doesn't take fall damage.
- Pinning ALL axes with setPos (X/Z too) freezes the mob — it then ignores knockback and feels stuck.
  Only pin the driven axis.

## Verified

Server-side log (see the `headless-ai-test-harness` skill): zombies rose `risen` 0→0.6→1.2…→3.0 and
reached `y=tgtY, dy=0` on the wall top. With a velocity-only approach they never left the ground.
