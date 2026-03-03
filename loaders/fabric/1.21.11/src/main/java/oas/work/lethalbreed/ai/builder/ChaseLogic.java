/**
 * Project: Lethal Breed
 * Responsibility: Chase and Climbing Logic for BuildStateMachine
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.ai.builder;

import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import oas.work.lethalbreed.config.ModConfig;
import oas.work.lethalbreed.mixin.EntityAccessor;
import java.util.concurrent.atomic.AtomicReference;

public class ChaseLogic {
    public static int process(ZombieEntity z, Vec3d tPos, AtomicReference<BlockPos> ab, AtomicReference<Boolean> th, int st, BuildStateMachine bsm) {
        double dy = tPos.y - z.getY();
        double d2 = z.squaredDistanceTo(tPos.x, z.getY(), tPos.z);
        if (z.isOnGround() && !th.get()) BrainProcessor.analyze(z, tPos, ab, th);
        
        if (z.horizontalCollision || z.getVelocity().lengthSquared() < 0.01 || d2 < 4.0) {
            if (++st > 1) { 
                BlockPos lb = ab.get();
                if (lb == null) lb = ObstructionAnalyzer.getStuckBlock(((EntityAccessor)z).getWorld(), z, tPos);
                if (lb == null && z.horizontalCollision) {
                    lb = z.getBlockPos().offset(z.getHorizontalFacing());
                    if (!ObstructionAnalyzer.isBlocking(((EntityAccessor)z).getWorld(), lb)) lb = lb.up();
                }
                if (lb != null && ObstructionAnalyzer.isBlocking(((EntityAccessor)z).getWorld(), lb)) {
                    z.getNavigation().stop(); bsm.setLockedBlock(lb); bsm.setStuckTicks(0); return 2;
                }
            }
        } else st = 0;
        bsm.setStuckTicks(st);
        if (dy > 0.8 && ConstructionCoordinator.shouldClimb(z, tPos)) return startClimb(z, bsm);
        if (z.isOnGround() && MovementCoordinator.tryBuild(z, tPos)) {
            z.getNavigation().stop(); bsm.setGlobalCooldown(ModConfig.INSTANCE.movement.buildGlobalCooldownTicks); return 0;
        }
        z.getNavigation().startMovingTo(tPos.x, tPos.y, tPos.z, 1.0);
        return 0;
    }

    private static int startClimb(ZombieEntity z, BuildStateMachine bsm) {
        BlockPos base = PackPlacementLogic.getBetterConstructionPos(z);
        if (!base.equals(z.getBlockPos())) {
            z.refreshPositionAndAngles(base.getX() + 0.5, z.getY(), base.getZ() + 0.5, z.getYaw(), z.getPitch());
        }
        BlockPos lb = ObstructionAnalyzer.getObstruction(((EntityAccessor)z).getWorld(), base, z);
        if (lb != null) { z.getNavigation().stop(); bsm.setLockedBlock(lb); return 2; }
        ConstructionCoordinator.freezeAndCenter(z);
        z.getJumpControl().setActive();
        bsm.setBasePos(base); bsm.setJumpTimer(0);
        return 1;
    }
}
