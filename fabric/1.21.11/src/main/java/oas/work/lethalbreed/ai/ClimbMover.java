package oas.work.lethalbreed.ai;

import net.minecraft.entity.LivingEntity;
import oas.work.lethalbreed.config.ModConfig;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.Vec3d;

public class ClimbMover {
    public static void applyClimb(ZombieEntity zombie) {
        LivingEntity target = zombie.getTarget();
        Vec3d velocity = zombie.getVelocity();
        double vy = ModConfig.INSTANCE.movement.climbVerticalSpeed;
        
        double vx = velocity.x;
        double vz = velocity.z;

        if (target != null) {
            double dx = target.getX() - zombie.getX();
            double dz = target.getZ() - zombie.getZ();
            Vec3d dir = new Vec3d(dx, 0, dz).normalize();
            vx = dir.x * ModConfig.INSTANCE.movement.climbHorizontalSpeed;
            vz = dir.z * ModConfig.INSTANCE.movement.climbHorizontalSpeed;
        }

        zombie.setVelocity(vx, vy, vz);
        zombie.velocityDirty = true;
    }
}