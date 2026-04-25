package oas.work.lethalbreed.ai.builder;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

public class BuildConditions {
    public static boolean canStart(Zombie zombie, int state, TargetLogic targetLogic) {
        LivingEntity target = zombie.getTarget();

        if (target != null && target.isAlive()) {
            if (state != 0) return true;
            double dy = target.getY() - zombie.getY();
            double distSq = zombie.distanceToSqr(target.getX(), zombie.getY(), target.getZ());
            return !(distSq < 2.5 && Math.abs(dy) < 1.25 && !isNearHole(zombie, target));
        }

        if (targetLogic != null && targetLogic.hasSound(zombie)) {
            return true;
        }

        return false;
    }

    public static boolean shouldReset(Zombie z, TargetLogic tl) {
        LivingEntity t = z.getTarget();
        if (t == null || !z.getSensing().hasLineOfSight(t)) return false;
        double d2 = z.distanceToSqr(t.getX(), z.getY(), t.getZ());
        return d2 < 9.0 && Math.abs(t.getY() - z.getY()) < 1.5 && tl.hasSound(z);
    }

    private static boolean isNearHole(Zombie z, LivingEntity t) {
        Vec3 dir = new Vec3(t.getX() - z.getX(), 0, t.getZ() - z.getZ()).normalize();
        BlockPos p = BlockPos.containing(z.getX() + dir.x * 0.8, z.getY(), z.getZ() + dir.z * 0.8);
        Level world = z.level();
        return world.getBlockState(p.below()).canBeReplaced();
    }
}