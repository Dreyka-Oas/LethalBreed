---
name: zombies-float-in-water-add-floatgoal
description: Use when LethalBreed zombies SINK in water and walk along the bottom instead of floating/swimming at the surface to keep chasing across water. Vanilla zombies lack the FloatGoal every other land mob has — add it back via a registerGoals mixin rather than fighting buoyancy with setPos/setDeltaMovement.
---

# Zombies must FLOAT in water — add the vanilla FloatGoal they lack

## Symptom

Zombies pursuing a target across a lake/river drop to the bottom and trudge along the floor (or just
stall at the water's edge) instead of swimming at the surface like a player or drowned. They lose the
chase whenever water is in the way, and submerged ones start converting to drowned.

## Cause

Vanilla `Zombie.registerGoals` deliberately does NOT add a `FloatGoal`. Almost every other land mob
(cows, pigs, skeletons, etc.) gets `goalSelector.addGoal(0, new FloatGoal(this))`, which is what keeps
them bobbing at the surface. Without it a zombie has reduced gravity in water but no upward impulse, so it
sinks slowly and walks the bottom — intended vanilla behaviour (it drives drowned conversion).

## Fix — add FloatGoal via a registerGoals mixin (do NOT nudge buoyancy from the scheduler)

`FloatGoal` does two things: in its constructor it sets `mob.getNavigation().setCanFloat(true)` (so the
ground pathfinder will route across water), and each tick the mob is in water it calls
`getJumpControl().jump()` (swim up). This runs as a normal vanilla goal on the entity's OWN server-AI
tick, every tick, on the main thread — so it needs none of the timing workarounds the mod's
`END_SERVER_TICK` scheduler does. Crucially, the upward motion goes through the jump control, which
vanilla movement honours — a `deltaMovement.y` set from `END_SERVER_TICK` would be cancelled (see
`entity-velocity-not-applying-use-setpos`), and per-bucket throttling (1 in 5+ ticks) is too sparse to
counter the sink. Let the goal do it.

`src/main/java/com/dreykaoas/lethalbreed/mixin/ZombieFloatInWaterMixin.java`:

```java
@Mixin(Zombie.class)
public abstract class ZombieFloatInWaterMixin {
    @Shadow @Final protected GoalSelector goalSelector;

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void lethalbreed$addFloatGoal(CallbackInfo ci) {
        if (!LethalBreedConfig.floatInWater) {
            return;
        }
        this.goalSelector.addGoal(0, new FloatGoal((Mob) (Object) this));
    }
}
```

Key points:
- `@Shadow @Final protected GoalSelector goalSelector;` reaches the inherited `Mob.goalSelector` field —
  no public getter exists, so shadow it (it is `final`, so include `@Final`).
- Priority `0`, matching vanilla land mobs, so floating wins over wander/pursuit.
- Config-gated by `LethalBreedConfig.floatInWater` (default `true`). Read at construction
  (`registerGoals`), same as `ZombieGoalSuppressMixin` — toggling at runtime needs a respawn/reload.
- Register the mixin in `lethalbreed.mixins.json` under `"mixins"`.

## Verify

`gradlew build` SUCCESSFUL. In-world: spawn zombies, lure them across water — they rise to and bob at the
surface and swim toward the target instead of sinking. Set `floatInWater = false` and they sink/walk the
bottom again (vanilla). Note `blockDrowned` already discards drowned on load, and surface-floating zombies
stay un-submerged so they no longer convert.
