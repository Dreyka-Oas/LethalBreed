package oas.work.lethalbreed.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.Vec3d;

public class PanicMovement {
    public static void execute(ZombieEntity zombie) {
        LivingEntity target = zombie.getTarget();
        double distSq = (target != null) ? zombie.squaredDistanceTo(target) : 400;
        if (distSq < 64.0) {
            Vec3d targetPos = (target != null) ? new Vec3d(target.getX(), target.getY(), target.getZ()) : null;
            Vec3d fleePos = (targetPos != null) ? 
                NoPenaltyTargeting.findFrom(zombie, 16, 7, targetPos) : 
                NoPenaltyTargeting.find(zombie, 16, 7);
            if (fleePos != null) {
                zombie.getNavigation().startMovingTo(fleePos.x, fleePos.y, fleePos.z, 1.3);
            }
        } else {
            zombie.getNavigation().stop();
        }
    }
}





