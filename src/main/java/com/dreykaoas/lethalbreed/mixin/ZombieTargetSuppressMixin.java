package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes the zombie's vanilla target-selection goals (NearestAttackableTargetGoal for players/villagers,
 * HurtByTargetGoal, etc.) so LethalBreed's own "nearest living entity" pick is authoritative. Without
 * this the vanilla goals keep re-locking the target (usually the player) every tick and fight our
 * retargeting, so a zombie would not switch to a closer entity. Config-gated (default ON).
 */
@Mixin(Zombie.class)
public abstract class ZombieTargetSuppressMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void lethalbreed$clearVanillaTargeting(CallbackInfo ci) {
        if (!TargetingConfig.forceNearestTarget) {
            return;
        }
        Mob self = (Mob) (Object) this;
        ((MobGoalsAccessor) self).lethalbreed$targetSelector().removeAllGoals(g -> true);
    }
}
