/**
 * Project: Lethal Breed
 * Responsibility: Mixin to Trigger Logic on Mutant Death
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ZombieEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MutantDeathMixin {
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeathSpawn(DamageSource damageSource, CallbackInfo ci) {
        if ((Object)this instanceof ZombieEntity zombie) {
            oas.work.lethalbreed.MutantLogic.onDeath(zombie);
        }
    }
}
