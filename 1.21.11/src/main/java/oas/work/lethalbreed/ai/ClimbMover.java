package oas.work.lethalbreed.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import oas.work.lethalbreed.config.ModConfig;

public class ClimbMover {
    public static void applyClimb(Zombie zombie) {
        LivingEntity target = zombie.getTarget();
        Vec3 velocity = zombie.getDeltaMovement();
        double vy = ModConfig.INSTANCE.movement.climbVerticalSpeed;
        
        double vx = velocity.x;
        double vz = velocity.z;

        if (target != null) {
            double dx = target.getX() - zombie.getX();
            double dz = target.getZ() - zombie.getZ();
            Vec3 dir = new Vec3(dx, 0, dz).normalize();
            vx = dir.x * ModConfig.INSTANCE.movement.climbHorizontalSpeed;
            vz = dir.z * ModConfig.INSTANCE.movement.climbHorizontalSpeed;
        }

        zombie.setDeltaMovement(vx, vy, vz);
        zombie.hurtMarked = true;
    }
}








