---
name: zombie-jump-dynamic-jump-boost-effect
description: Use when a LethalBreed zombie's jump must react to the vanilla Jump Boost potion effect (or any mod that adds it) — e.g. "I gave the zombie Jump Boost but its jump wasn't boosted". Our climb/leap jumps are driven by a manual velocity impulse, so they must fold in the live effect dynamically instead of using the hard-coded config power.
---

# Zombie jump — fold the live Jump Boost effect into the impulse (don't hard-code)

## Why it broke

Our zombies don't jump with the vanilla key path — `setJumping(true)` is a no-op from our scheduler, so we
launch jumps with a direct `entity.setDeltaMovement(0, power, 0)` impulse using a fixed config value
(`pillarJumpPower`, `leapUpward`, the dismount hop's `0.42`). Vanilla `getJumpPower()` adds the Jump Boost
contribution on top; our manual impulse bypassed that, so a zombie given the Jump Boost potion jumped the
same height. The boost must be added by us, read **live** each jump (dynamic, never hard-coded).

## Fix

Add the vanilla jump-boost term to every upward jump impulse:

```java
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Upward jump impulse with the live Jump Boost effect folded in (vanilla adds 0.1 * (amplifier + 1)). */
private double jumpVelocity(double base) {
    MobEffectInstance jump = entity.getEffect(MobEffects.JUMP_BOOST);
    return jump != null ? base + 0.1 * (jump.getAmplifier() + 1) : base;
}
```

Apply it to all three jump sites in `SmartZombie`: the pillar-climb jump (`jumpVelocity(pillarJumpPower)`),
the reach-target dismount hop (`jumpVelocity(0.42)`), and the leap/pounce (`jumpVelocity(leapUpward * leapFactor)`).

## Key facts

- **The field is `MobEffects.JUMP_BOOST` in 1.21.11 Mojang mappings** (registry id `jump_boost`), NOT
  `MobEffects.JUMP` (an older name). Verify field names against the mapped MC jar when unsure — extract
  `net/minecraft/world/effect/MobEffects.class` and grep its field strings; don't guess.
- `LivingEntity.getJumpBoostPower()` exists but is **protected** — not callable on the wrapped `entity`, so
  compute the same `0.1 * (amplifier + 1)` ourselves.
- `entity.getEffect(holder)` returns the `MobEffectInstance` or `null`; `getAmplifier()` is 0-based (Jump
  Boost I → amplifier 0 → +0.1).
- Read the effect at jump time (live), so it tracks the effect being added/removed/refreshed dynamically.

## Don't regress

- Any NEW jump/launch impulse must go through `jumpVelocity(...)`, not a bare constant, or it silently
  ignores Jump Boost again.
- Keep using the velocity-impulse approach (see `zombie-ascend-jump-and-place-not-setpos`) — `setJumping`
  won't trigger from the scheduler, and that's also why vanilla's own jump-power path never runs for us.
