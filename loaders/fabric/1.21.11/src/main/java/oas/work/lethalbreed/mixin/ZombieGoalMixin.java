/**
 * Project: Lethal Breed
 * Responsibility: AI Goal Registration Mixin
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.mixin;

import net.minecraft.entity.mob.ZombieEntity;
import oas.work.lethalbreed.ai.builder.ZombieBuildGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombieEntity.class)
public abstract class ZombieGoalMixin {
    @Inject(method = "initGoals", at = @At("HEAD"))
    private void addCustomGoals(CallbackInfo ci) {
        ZombieEntity zombie = (ZombieEntity) (Object) this;
        MobEntityAccessor accessor = (MobEntityAccessor) zombie;
        
        // Clean default targets to avoid duplicates and lag
        accessor.getTargetSelector().clear(g -> true);

        // Goals
        accessor.getGoalSelector().add(0, new oas.work.lethalbreed.ai.FleeExplosionGoal(zombie));
        accessor.getGoalSelector().add(1, new oas.work.lethalbreed.ai.KamikazeGoal(zombie));
        accessor.getGoalSelector().add(2, new oas.work.lethalbreed.ai.ZombiePanicGoal(zombie));
        accessor.getGoalSelector().add(3, new ZombieBuildGoal(zombie));

        // Targets (Highest priority on the closest visible target)
        accessor.getTargetSelector().add(0, new oas.work.lethalbreed.ai.ClosestVisibleTargetGoal(zombie));
    }
}