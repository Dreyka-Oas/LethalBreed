package oas.work.lethalbreed.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

public class PanicMovement {
    public static void execute(Zombie zombie) {
        LivingEntity target = zombie.getTarget();
        double distSq = (target != null) ? zombie.distanceToSqr(target) : 400;
        if (distSq < 64.0) {
            Vec3 targetPos = (target != null) ? new Vec3(target.getX(), target.getY(), target.getZ()) : null;
            Vec3 fleePos = (targetPos != null) ? 
                DefaultRandomPos.getPosAway(zombie, 16, 7, targetPos) : 
                DefaultRandomPos.getPos(zombie, 16, 7);
            if (fleePos != null) {
                zombie.getNavigation().moveTo(fleePos.x, fleePos.y, fleePos.z, 1.3);
            }
        } else {
            zombie.getNavigation().stop();
        }
    }
}







