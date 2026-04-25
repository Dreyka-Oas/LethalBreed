package oas.work.lethalbreed.ai.builder;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class ConstructionCoordinator {
    public static void freezeAndCenter(ZombieEntity z) {
        BlockPos pos = z.getBlockPos();
        z.refreshPositionAndAngles(pos.getX() + 0.5, z.getY(), pos.getZ() + 0.5, z.getYaw(), z.getPitch());
        z.getNavigation().stop();
        z.setVelocity(0, z.getVelocity().y, 0);
        z.velocityDirty = true;
    }

    public static boolean shouldClimb(ZombieEntity z, Vec3d t) {
        double dy = t.y - z.getY();
        double distSq = z.squaredDistanceTo(t.x, z.getY(), t.z);
        return (z.horizontalCollision || distSq < 36.0) && dy > 0.8 && z.isOnGround();
    }
}




