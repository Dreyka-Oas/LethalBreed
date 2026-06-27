---
name: zombie-targeting-nearest-and-vision-los
description: Use when LethalBreed zombies won't switch to a closer target (keep chasing the player), or when you need vision that sees through translucent blocks (glass) but not opaque ones. Covers stripping vanilla target goals so our nearest-pick is authoritative, plus opaque-only line-of-sight acquisition.
---

# Zombie targeting (nearest) + vision line-of-sight

## Nearest-entity targeting won't switch — strip vanilla target goals

`LODManager`/`TargetSelector.findNearest` picks the nearest valid living entity every tick, but the zombie
kept chasing the player anyway. Cause: the **vanilla target-selection goals**
(`NearestAttackableTargetGoal<Player>`, `HurtByTargetGoal`, …) re-lock `getTarget()` every tick and fight
our pick.

Fix: remove them so our pick is authoritative.
- `ZombieTargetSuppressMixin` injects TAIL of `Zombie.registerGoals` and calls
  `targetSelector.removeAllGoals(g -> true)` (config `forceNearestTarget`, default ON).
- Reach the inherited `targetSelector` field via an `@Accessor` on `Mob` (same `MobGoalsAccessor` used for
  `goalSelector` — a `@Shadow` from a `Zombie` target fails, see
  `mixin-shadow-inherited-field-use-accessor`).
- In `LODManager.classify`, after picking the nearest, also `entity.setTarget(nearest)` so melee/look track
  the switch immediately (even mid water/pillar where `tick()` returns early).

`TargetSelector.isValid` excludes other zombies, bosses (dragon/wither), armor stands, and
creative/spectator players; everything else is fair game and the nearest wins.

## Vision: see through translucent, not opaque

Requirement: zombies see a target through glass/ice/leaves but NOT through solid walls; a target behind a
solid wall is found by **sound** instead (sound passes through walls, by design).

`TargetSelector.findNearest` gates each candidate on `canSee()` when `requireLineOfSight` (default ON):
- Coarse voxel walk (0.5-block steps) from the zombie's eye to the target's eye.
- A block blocks sight only if `state.canOcclude()` is true (opaque full cubes: stone, dirt). Glass, ice
  and leaves return false → see-through.

### Hearing through walls must ALSO make a target (close hidden entity beats far visible one)
A candidate is detected if SEEN (LOS) **or HEARD** — within hearing range (`soundBaseRadius`, when
`soundEnabled`) sound passes through solid walls. Without hearing, `findNearest` skipped the no-LOS
candidate entirely and a villager hidden behind two blocks lost to a much farther entity that happened to
have line of sight ("zombie ran to the far one — not logical"). In the loop:
`boolean heard = distSq <= soundBaseRadius² && isAudible(e);` and only require `canSee()` when
`requireLineOfSight && !heard`. The nearest DETECTED entity then wins, so the close hidden one is targeted
and the zombie digs to it.

### Hearing requires NOISE, not mere proximity (don't see through walls)
`heard` is NOT pure distance. A motionless, silent entity within `soundBaseRadius` was still "heard" through
walls — a trapped villager that can't move (no sound) got targeted through 2 opaque blocks ("ils voient à
travers les blocs au lieu d'entendre… ne bouge pas donc pas de bruit"). Gate hearing on the entity actually
emitting noise this tick — `isAudible(LivingEntity e)`:
- **walking** = horizontal `getDeltaMovement()` magnitude `>= soundMoveThreshold` **and** `!isCrouching()`
  (sneaking is silent — same rule as player footsteps in `SoundEventBus.tickPlayers`),
- **acting** = `e.swinging || e.isUsingItem()` (arm swing = attack/place/break/mine; using item = eat/drink),
- **hurt** = `e.hurtTime > 0` (cry on taking damage).
Horizontal delta only (ignore gravity Y so a standing mob isn't "walking"). A silent immobile entity is then
sight-only; behind opaque blocks it's neither seen nor heard → not a target until it makes noise. Member
names verified in the mapped jar (`swinging`/`hurtTime`/`isUsingItem` on `LivingEntity`, `isCrouching`/
`getDeltaMovement` on `Entity`).

Note: `level.clip(... ClipContext.Block.VISUAL/COLLIDER ...)` would treat glass as a blocker (it has a
shape), so it does NOT give "see through glass". The manual `canOcclude` walk is what does.

## Don't regress

- Keep vanilla target goals stripped, or nearest-switching breaks again.
- Vision blocker test is `canOcclude()` (opaque), not collision/visual shape (which glass has).
- Hidden-target detection must keep falling back to sound — don't also gate sound on line of sight.
- Hearing must require real noise (`isAudible`) — proximity alone is NOT heard, or zombies see through walls.
