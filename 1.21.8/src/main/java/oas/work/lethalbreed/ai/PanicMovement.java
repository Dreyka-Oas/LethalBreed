package oas.work.lethalbreed.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

public class PanicMovement {
    public static void execute(Zombie zombie) {
        LivingEntity target = zombie.getTarget();
        double distSq = (target != null) ? zombie.distanceToSqr(target) : 400;
        if (distSq < 64.0) {
            Vec3 targetPos = (target != null) ? target.position() : null;
            Vec3 fleePos = findFrom(zombie, 16, 7, targetPos);
            if (fleePos != null) {
                zombie.getNavigation().moveTo(fleePos.x, fleePos.y, fleePos.z, 1.3);
            }
        } else {
            zombie.getNavigation().stop();
        }
    }

    private static Vec3 findFrom(Zombie zombie, int i, int j, Vec3 vec3) {
        if (vec3 == null) {
            return find(zombie, i, j);
        }
        double x = zombie.getX() - vec3.x;
        double z = zombie.getZ() - vec3.z;
        double len = Math.sqrt(x*x + z*z);
        if (len == 0) return null;
        x /= len;
        z /= len;
        return new Vec3(zombie.getX() + x * i, zombie.getY(), zombie.getZ() + z * i);
    }

    private static Vec3 find(Zombie zombie, int i, int j) {
        return new Vec3(
            zombie.getX() + (zombie.getRandom().nextDouble() - 0.5) * i * 2,
            zombie.getY(),
            zombie.getZ() + (zombie.getRandom().nextDouble() - 0.5) * i * 2
        );
    }
}