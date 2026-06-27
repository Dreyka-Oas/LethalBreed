---
name: zombie-ascend-jump-and-place-not-setpos
description: Use when LethalBreed zombies look like they FLY / levitate up walls instead of climbing like a player, OR when they refuse to climb at all (stay grounded, only climb after being hit). The player-like behaviour is a real upward VELOCITY IMPULSE plus placing a support block to land one higher. Always ascend by jump-and-place, never setPos, and never setJumping from the scheduler.
---

# Zombies ascend by VELOCITY-IMPULSE jump + place — not setPos, not setJumping

## Symptoms

- A zombie reaching an elevated target rises **smoothly up a wall**, gliding through air — the user reads
  this as "they fly". (Old `setPos` spider-climb.)
- OR zombies **refuse to climb** — they stand at the wall base and only start climbing **after the player
  hits one**.
- OR they trigger the pillar but **never gain height** (`risen=0` forever in `[ClimbDbg]`).

## The working design

Route ALL ascent through one jump-and-place pillar (`initiatePillar` / `pillarStep` in `SmartZombie`),
wall or no wall, driven each tick from the scheduler's climber pass (END_SERVER_TICK):

```java
entity.getNavigation().stop();                 // don't let a path drag it off the spot
if (entity.onGround()) {
    pillarColX = entity.blockPosition().getX();
    pillarColZ = entity.blockPosition().getZ();
    pillarStandY = entity.blockPosition().getY();
    entity.setDeltaMovement(0.0, 0.42, 0.0);   // upward impulse ~vanilla jump → clears one block
    entity.hurtMarked = true;                  // sync the velocity to clients
} else {
    // airborne and clear of the block we left → drop a support into that cell, land one block higher
    if (entity.getY() >= pillarStandY + 1.0) {
        ctx.blockOps().enqueuePlace(new BlockPos(pillarColX, pillarStandY, pillarColZ));
    }
}
```

## Three things that DID NOT work (verified with the headless ClimbTest arena + `[ClimbDbg]` logs)

1. **`setPos` vertical lift** = the "flying" look. Never reintroduce it for climbing.
2. **`entity.setJumping(true)` from END_SERVER_TICK does nothing** — the mob stays glued to the ground
   (`risen=0`, `ground=true` every tick). The `jumping` flag is consumed by the mob's own aiStep timing,
   not by a flag set after the entity already ticked.
3. **`setPos(colX+0.5, getY(), colZ+0.5)` to "stay on the column" breaks the jump** — the teleport resets
   `onGround`, so the next jump's ground check fails. The `ground` flag flickers from the teleport, looking
   like a jump, but `risen` stays 0.

## What works

A **direct upward `setDeltaMovement` impulse** set at END_SERVER_TICK survives into the next tick's
`travel()` (gravity only shaves ~0.08 off it), so the zombie genuinely rises ~1.1 blocks and lands on the
support block placed in the cell it left. `risen` then ratchets 1 → 2 → … to the target. Note
`entity.hasImpulse` does NOT exist in these Mojmap mappings — use `hurtMarked` for the velocity sync.

Other key points:
- Place the support in the cell the feet **just left** (`pillarStandY`), only once airborne above it, so
  you never place a block inside the zombie (suffocation) and it lands one block higher.
- Make `tick()` early-return on `pillaring` so vanilla nav/leap don't fight the jump. The block queue
  drains AFTER the climber pass in the same tick, so the support exists before next tick's gravity check.
- **Trigger the climb the moment the target is perched close above and unreachable — do NOT gate it on the
  horizontal "stuck" heuristic.** A zombie jittering at the wall base rarely registers as stuck, so gating
  on stuck makes it climb only after a hit (which knocks it still long enough to trip the flag).

## Verify

Enable `devClimbTest=true`, run `gradlew runServer` headless, watch `[ClimbDbg]`: `risen` should climb
1→N and the zombies end at the villager's Y (`dy≈0`) on top of the wall. Turn `devClimbTest` back off
when done.
