package oas.work.lethalbreed.ai.builder;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import oas.work.lethalbreed.config.ModConfig;
import oas.work.lethalbreed.mixin.EntityAccessor;
import java.util.concurrent.atomic.AtomicReference;

public class ChaseLogic {
    public static int process(ZombieEntity z, Vec3d tPos, AtomicReference<BlockPos> ab, AtomicReference<Boolean> th, int st, BuildStateMachine bsm, boolean hasVisualTarget) {
        double dy = tPos.y - z.getY();
        
        if (hasVisualTarget && z.isOnGround()) {
            BlockPos obstruction = ObstructionAnalyzer.getHorizontal(((EntityAccessor)z).getWorld(), z, tPos);
            if (obstruction != null) {
                z.getNavigation().stop();
                bsm.setLockedBlock(obstruction);
                return 2;
            }
        }
        
        if (z.isOnGround() && !th.get()) BrainProcessor.analyze(z, tPos, ab, th);
        if (dy > 0.8 && ConstructionCoordinator.shouldClimb(z, tPos)) {
            BlockPos bp = PackPlacementLogic.getBetterConstructionPos(z);
            if (!bp.equals(z.getBlockPos())) z.refreshPositionAndAngles(bp.getX() + 0.5, z.getY(), bp.getZ() + 0.5, z.getYaw(), z.getPitch());
            BlockPos lb = ObstructionAnalyzer.getObstruction(((EntityAccessor)z).getWorld(), bp, z);
            if (lb != null) { z.getNavigation().stop(); bsm.setLockedBlock(lb); return 2; }
            ConstructionCoordinator.freezeAndCenter(z);
            z.getJumpControl().setActive();
            bsm.setBasePos(bp); bsm.setJumpTimer(0); return 1;
        }
        if (z.horizontalCollision || z.getVelocity().lengthSquared() < 0.01 || z.squaredDistanceTo(tPos.x, z.getY(), tPos.z) < 4.0) {
            if (++st > 15) { 
                BlockPos lb = StuckLogic.findBlocking(z, tPos, ab.get());
                if (lb != null) { z.getNavigation().stop(); bsm.setLockedBlock(lb); bsm.setStuckTicks(0); return 2; }
            }
        } else st = 0;
        bsm.setStuckTicks(st);
        if (z.isOnGround() && MovementCoordinator.tryBuild(z, tPos)) {
            z.getNavigation().stop(); bsm.setGlobalCooldown(ModConfig.INSTANCE.movement.buildGlobalCooldownTicks); return 0;
        }
        z.getNavigation().startMovingTo(tPos.x, tPos.y, tPos.z, 1.0);
        return 0;
    }
}






