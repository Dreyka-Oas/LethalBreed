package oas.work.lethalbreed.ai.builder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

public class ConstructionCoordinator {
    public static void freezeAndCenter(Zombie z) {
        BlockPos pos = z.blockPosition();
        z.snapTo(pos.getX() + 0.5, z.getY(), pos.getZ() + 0.5, z.getYRot(), z.getXRot());
        z.getNavigation().stop();
        z.setDeltaMovement(0, z.getDeltaMovement().y, 0);
        z.hurtMarked = true;
    }

    public static boolean shouldClimb(Zombie z, Vec3 t) {
        double dy = t.y - z.getY();
        double distSq = z.distanceToSqr(t.x, z.getY(), t.z);
        return (z.horizontalCollision || distSq < 36.0) && dy > 0.8 && z.onGround();
    }
}








