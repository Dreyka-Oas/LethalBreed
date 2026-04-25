package oas.work.lethalbreed.ai.builder;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import oas.work.lethalbreed.mixin.EntityAccessor;

public class BridgeCoordinator {
    public static boolean tryBridge(ZombieEntity z, LivingEntity target) {
        if (!z.isOnGround()) return false;

        World world = ((EntityAccessor)z).getWorld();
        double dx = target.getX() - z.getX();
        double dz = target.getZ() - z.getZ();
        Vec3d dir = new Vec3d(dx, 0, dz).normalize();
        
        for (double d = 0.5; d <= 2.0; d += 0.5) {
            double cx = z.getX() + dir.x * d;
            double cz = z.getZ() + dir.z * d;
            BlockPos checkPos = BlockPos.ofFloored(cx, z.getY(), cz);
            
            if (world.getBlockState(checkPos.down()).isReplaceable()) {
                stopAndSnap(z);
                BlockPos bridgePos = checkPos.down();
                BlockSetter.placeDirt(world, bridgePos);
                return true;
            }
        }
        return false;
    }

    private static void stopAndSnap(ZombieEntity z) {
        z.getNavigation().stop();
        z.setVelocity(0, 0, 0);
        z.velocityDirty = true;
        BlockPos current = z.getBlockPos();
        z.refreshPositionAndAngles(current.getX() + 0.5, z.getY(), current.getZ() + 0.5, z.getYaw(), z.getPitch());
    }
}






