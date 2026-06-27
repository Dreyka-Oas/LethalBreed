package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Make zombies FLOAT and swim at the water surface instead of sinking and walking along the bottom
 * (config-gated, default ON). Vanilla land mobs carry a {@link FloatGoal} that keeps them bobbing at the
 * surface; zombies deliberately lack it (so they sink and convert to drowned). We add it back after the
 * zombie registers its vanilla goals.
 *
 * <p>{@code FloatGoal} sets the navigation to {@code canFloat} and, each tick the zombie is in water,
 * makes it jump (swim up) — exactly the player/drowned-style surface bobbing. Running as a vanilla goal
 * on the entity's own server-AI tick means it applies every tick on the main thread and needs none of the
 * setPos/setDeltaMovement timing workarounds the mod's END_SERVER_TICK scheduler does (the upward impulse
 * goes through the jump control, which vanilla movement honours — see the float-in-water skill).
 */
@Mixin(Zombie.class)
public abstract class ZombieFloatInWaterMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void lethalbreed$addFloatGoal(CallbackInfo ci) {
        if (!LethalBreedConfig.floatInWater) {
            return;
        }
        // goalSelector is declared on Mob, so reach it via the accessor. Priority 0: floating takes
        // precedence over wander/pursuit, same as vanilla land mobs.
        Mob self = (Mob) (Object) this;
        ((MobGoalsAccessor) self).lethalbreed$goalSelector().addGoal(0, new FloatGoal(self));
    }
}
