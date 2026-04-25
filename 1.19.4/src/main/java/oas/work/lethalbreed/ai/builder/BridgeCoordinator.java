package oas.work.lethalbreed.ai.builder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import oas.work.lethalbreed.mixin.EntityAccessor;

public class BridgeCoordinator {
    public static boolean tryBridge(Zombie z, LivingEntity target) {
        if (!z.isOnGround()) return false;

        Level world = ((EntityAccessor)z).getWorld();
        double dx = target.getX() - z.getX();
        double dz = target.getZ() - z.getZ();
        Vec3 dir = new Vec3(dx, 0, dz).normalize();
        
        for (double d = 0.5; d <= 2.0; d += 0.5) {
            double cx = z.getX() + dir.x * d;
            double cz = z.getZ() + dir.z * d;
            BlockPos checkPos = new BlockPos((int) cx, (int) z.getY(), (int) cz);
            
            if (world.getBlockState(checkPos.below()).getMaterial().isReplaceable()) {
                stopAndSnap(z);
                BlockPos bridgePos = checkPos.below();
                BlockSetter.placeDirt(world, bridgePos);
                return true;
            }
        }
        return false;
    }

    private static void stopAndSnap(Zombie z) {
        z.getNavigation().stop();
        z.setDeltaMovement(0, 0, 0);
        z.hasImpulse = true;
        BlockPos current = z.blockPosition();
        z.moveTo(current.getX() + 0.5, z.getY(), current.getZ() + 0.5, z.getYRot(), z.getXRot());
    }
}






