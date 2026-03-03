/**
 * Project: Lethal Breed
 * Responsibility: AI Building Condition Evaluation
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.ai.builder;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import oas.work.lethalbreed.mixin.EntityAccessor;

public class BuildConditions {
    public static boolean canStart(ZombieEntity zombie, int state) {
        LivingEntity target = zombie.getTarget();
        if (target == null) return false;
        if (state != 0) return true;
        
        double dy = target.getY() - zombie.getY();
        double distSq = zombie.squaredDistanceTo(target.getX(), zombie.getY(), target.getZ());
        return !(distSq < 2.5 && Math.abs(dy) < 1.25 && !isNearHole(zombie, target));
    }

    private static boolean isNearHole(ZombieEntity z, LivingEntity t) {
        Vec3d dir = new Vec3d(t.getX() - z.getX(), 0, t.getZ() - z.getZ()).normalize();
        BlockPos p = BlockPos.ofFloored(z.getX() + dir.x * 0.8, z.getY(), z.getZ() + dir.z * 0.8);
        return ((EntityAccessor)z).getWorld().getBlockState(p.down()).isReplaceable();
    }
}
