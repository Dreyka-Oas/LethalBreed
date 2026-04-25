package oas.work.lethalbreed.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import oas.work.lethalbreed.config.ModConfig;
import oas.work.lethalbreed.mixin.EntityAccessor;
import java.util.List;

public class TargetFinder {
    public static boolean isValid(LivingEntity e) {
        if (e == null || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof PlayerEntity p) return !p.isCreative(); if (e instanceof MerchantEntity) return true; if (e instanceof IronGolemEntity) return true; return false;
    }

    public static LivingEntity find(ZombieEntity zombie) {
        double range = ModConfig.INSTANCE.attributes.zombieFollowRange;
        List<LivingEntity> entities = ((EntityAccessor)zombie).getWorld().getEntitiesByClass(LivingEntity.class, 
            zombie.getBoundingBox().expand(range, 10.0, range), TargetFinder::isValid);
        
        LivingEntity closest = null;
        double minDist = Double.MAX_VALUE;
        for (LivingEntity e : entities) {
            if (!zombie.canSee(e)) continue;
            double dist = zombie.squaredDistanceTo(e);
            if (dist < minDist) { minDist = dist; closest = e; }
        }
        return closest;
    }
}








