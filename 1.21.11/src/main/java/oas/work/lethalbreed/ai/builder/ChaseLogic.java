package oas.work.lethalbreed.ai.builder;

import oas.work.lethalbreed.config.ModConfig;
import oas.work.lethalbreed.mixin.EntityAccessor;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

public class ChaseLogic {
    public static int process(Zombie z, Vec3 tPos, AtomicReference<BlockPos> ab, AtomicReference<Boolean> th, int st, BuildStateMachine bsm, boolean hasVisualTarget) {
        double dy = tPos.y - z.getY();
        
        if (hasVisualTarget && z.onGround()) {
            BlockPos obstruction = ObstructionAnalyzer.getHorizontal(((EntityAccessor)z).getWorld(), z, tPos);
            if (obstruction != null) {
                z.getNavigation().stop();
                bsm.setLockedBlock(obstruction);
                return 2;
            }
        }
        
        if (z.onGround() && !th.get()) BrainProcessor.analyze(z, tPos, ab, th);
        if (dy > 0.8 && ConstructionCoordinator.shouldClimb(z, tPos)) {
            BlockPos bp = PackPlacementLogic.getBetterConstructionPos(z);
            if (!bp.equals(z.blockPosition())) z.snapTo(bp.getX() + 0.5, z.getY(), bp.getZ() + 0.5, z.getYRot(), z.getXRot());
            BlockPos lb = ObstructionAnalyzer.getObstruction(((EntityAccessor)z).getWorld(), bp, z);
            if (lb != null) { z.getNavigation().stop(); bsm.setLockedBlock(lb); return 2; }
            ConstructionCoordinator.freezeAndCenter(z);
            z.getJumpControl().jump();
            bsm.setBasePos(bp); bsm.setJumpTimer(0); return 1;
        }
        if (z.horizontalCollision || z.getDeltaMovement().lengthSqr() < 0.01 || z.distanceToSqr(tPos.x, z.getY(), tPos.z) < 4.0) {
            if (++st > 15) { 
                BlockPos lb = StuckLogic.findBlocking(z, tPos, ab.get());
                if (lb != null) { z.getNavigation().stop(); bsm.setLockedBlock(lb); bsm.setStuckTicks(0); return 2; }
            }
        } else st = 0;
        bsm.setStuckTicks(st);
        if (z.onGround() && MovementCoordinator.tryBuild(z, tPos)) {
            z.getNavigation().stop(); bsm.setGlobalCooldown(ModConfig.INSTANCE.movement.buildGlobalCooldownTicks); return 0;
        }
        z.getNavigation().moveTo(tPos.x, tPos.y, tPos.z, 1.0);
        return 0;
    }
}









