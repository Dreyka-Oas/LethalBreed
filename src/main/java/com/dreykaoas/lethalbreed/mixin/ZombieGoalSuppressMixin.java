package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional coexistence tweak (config-gated, default OFF): after a zombie registers its vanilla goals,
 * remove the idle wander/look goals that LethalBreed's flow-field navigation replaces. This saves CPU
 * and avoids those goals fighting our {@code moveTo}, while keeping vanilla target acquisition + melee
 * intact. Reduces the surface that Lithium's AI patches interact with.
 */
@Mixin(Zombie.class)
public abstract class ZombieGoalSuppressMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void lethalbreed$suppressWanderGoals(CallbackInfo ci) {
        if (!LethalBreedConfig.suppressVanillaWander) {
            return;
        }
        Mob self = (Mob) (Object) this;
        self.removeAllGoals(goal ->
                goal instanceof RandomStrollGoal
                        || goal instanceof WaterAvoidingRandomStrollGoal
                        || goal instanceof RandomLookAroundGoal
                        || goal instanceof LookAtPlayerGoal);
    }
}
