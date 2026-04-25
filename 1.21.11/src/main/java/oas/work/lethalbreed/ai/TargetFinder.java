package oas.work.lethalbreed.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.animal.golem.IronGolem;
import oas.work.lethalbreed.config.ModConfig;
import java.util.List;

public class TargetFinder {
    public static boolean isValid(LivingEntity e) {
        if (e == null || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof Player p) return !p.isCreative();
        if (e instanceof Npc) return true;
        if (e instanceof IronGolem) return true;
        return false;
    }

    public static LivingEntity find(Zombie zombie) {
        double range = ModConfig.INSTANCE.attributes.zombieFollowRange;
        List<LivingEntity> entities = zombie.level().getEntitiesOfClass(
            LivingEntity.class, zombie.getBoundingBox().inflate(range, 10.0, range),
            e -> isValid(e)
        );

        LivingEntity closest = null;
        double minDist = Double.MAX_VALUE;
        for (LivingEntity e : entities) {
            if (!zombie.getSensing().hasLineOfSight(e)) continue;
            double dist = zombie.distanceToSqr(e);
            if (dist < minDist) { minDist = dist; closest = e; }
        }
        return closest;
    }
}