/**
 * Project: Lethal Breed
 * Responsibility: Block Breaking Logic
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.ai.builder;

import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import oas.work.lethalbreed.config.ModConfig;
import net.minecraft.util.math.BlockPos;
import oas.work.lethalbreed.mixin.EntityAccessor;

public class BreakAction {
    public static boolean tick(ZombieEntity z, BlockPos target, int timer) {
        if (target == null) return true;
        z.getNavigation().stop();
        z.getLookControl().lookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        
        BlockState state = z.getEntityWorld().getBlockState(target);
        if (state.isAir()) {
            ((EntityAccessor)z).getWorld().setBlockBreakingInfo(z.getId(), target, -1);
            return true;
        }
        
        float speed = z.getMainHandStack().getMiningSpeedMultiplier(state);
        if (speed <= 1.0f) speed = 1.0f;
        
        // BOOST: Zombie mines X times faster than an unarmed player to be threatening
                int maxTime = (int) (20 / (speed * ModConfig.INSTANCE.breaking.breakSpeedMultiplier));
                if (maxTime < ModConfig.INSTANCE.breaking.breakMinTicks) maxTime = ModConfig.INSTANCE.breaking.breakMinTicks;
        int progress = (int) (((float)timer / maxTime) * 10);
        ((EntityAccessor)z).getWorld().setBlockBreakingInfo(z.getId(), target, progress);
        
        if (timer >= maxTime) {
            ((EntityAccessor)z).getWorld().setBlockBreakingInfo(z.getId(), target, -1);
            ((EntityAccessor)z).getWorld().breakBlock(target, true);
            return true;
        }
        return false;
    }
}
