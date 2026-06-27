---
name: mixin-shadow-inherited-field-use-accessor
description: Use when a LethalBreed mixin crashes at game load (not compile) with "InvalidMixinException @Shadow field <name> was not located in the target class". The field is declared on a SUPERCLASS of the target (e.g. goalSelector on Mob, not Zombie). @Shadow cannot reach it — add an @Accessor mixin on the declaring class and cast instead.
---

# @Shadow of an inherited field fails — use an @Accessor on the declaring class

## Symptom

The build is GREEN (`gradlew build` SUCCESSFUL) but `runClient` crashes during startup when the target
class first loads:

```
Mixin apply for mod lethalbreed failed ...ZombieFloatInWaterMixin -> net.minecraft.world.entity.monster.zombie.Zombie:
InvalidMixinException @Shadow field goalSelector was not located in the target class
net.minecraft.world.entity.monster.zombie.Zombie. No refMap loaded.
... MixinApplyError ... FAILED during APPLY
```

This class of bug compiles fine and is ONLY caught by actually loading the game — always launch after a
mixin change, never trust a green build alone.

## Cause

`@Shadow` resolves a member **on the targeted class**. `goalSelector` is declared on `Mob`, and the
mixin targeted `Zombie` (a subclass). Mixin's `@Shadow` does not walk up to a superclass field here, so
it fails to locate it. (Targeting `Mob` directly would work for the shadow but then applies to every mob,
not just zombies.)

## Fix — Accessor on the class that DECLARES the field

1. Make a small accessor interface mixin on the **declaring** class:

   ```java
   @Mixin(Mob.class)
   public interface MobGoalsAccessor {
       @Accessor("goalSelector")
       GoalSelector lethalbreed$goalSelector();
   }
   ```

2. In the zombie mixin, drop the `@Shadow`, cast the entity to the accessor, and call through it:

   ```java
   @Mixin(Zombie.class)
   public abstract class ZombieFloatInWaterMixin {
       @Inject(method = "registerGoals", at = @At("TAIL"))
       private void lethalbreed$addFloatGoal(CallbackInfo ci) {
           Mob self = (Mob) (Object) this;
           ((MobGoalsAccessor) self).lethalbreed$goalSelector().addGoal(0, new FloatGoal(self));
       }
   }
   ```

3. Register BOTH in `lethalbreed.mixins.json` `"mixins"`.

## Alternative already in this codebase

If you only need to REMOVE goals, `Mob` has the public method `removeAllGoals(Predicate)` — see
`ZombieGoalSuppressMixin`, which casts `(Mob)(Object)this` and calls it with no shadow/accessor at all.
There is no public `addGoal`, so adding a goal needs the accessor above.

## Rule

To touch an inherited (superclass-declared) field from a subclass-targeted mixin, use an `@Accessor`
mixin on the declaring class — never `@Shadow` it from the subclass target. And always run the game after
any mixin edit; mixin-apply errors are invisible to the compiler.
