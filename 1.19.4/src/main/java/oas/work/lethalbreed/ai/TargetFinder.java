package oas.work.lethalbreed.ai;

import oas.work.lethalbreed.config.ModConfig;
import oas.work.lethalbreed.mixin.EntityAccessor;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

public class TargetFinder {
    public static boolean isValid(LivingEntity e) {
        if (e == null || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof Player p) return !p.isCreative(); if (e instanceof AbstractVillager) return true; if (e instanceof IronGolem) return true; return false;
    }

    public static LivingEntity find(Zombie zombie) {
        double range = ModConfig.INSTANCE.attributes.zombieFollowRange;
        List<LivingEntity> entities = ((EntityAccessor)zombie).getWorld().getEntitiesOfClass(LivingEntity.class, 
            zombie.getBoundingBox().inflate(range, 10.0, range), TargetFinder::isValid);
        
        LivingEntity closest = null;
        double minDist = Double.MAX_VALUE;
        for (LivingEntity e : entities) {
            if (!zombie.hasLineOfSight(e)) continue;
            double dist = zombie.distanceToSqr(e);
            if (dist < minDist) { minDist = dist; closest = e; }
        }
        return closest;
    }
}








