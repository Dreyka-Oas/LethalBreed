package oas.work.lethalbreed.ai.builder;

import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.ZombieEntity;
import oas.work.lethalbreed.config.ModConfig;
import net.minecraft.util.math.BlockPos;
import oas.work.lethalbreed.mixin.EntityAccessor;

public class BreakAction {
    public static boolean tick(ZombieEntity z, BlockPos target, int timer) {
        if (target == null) return true;
        z.getNavigation().stop();
        z.getLookControl().lookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        BlockState state = ((oas.work.lethalbreed.mixin.EntityAccessor)z).getWorld().getBlockState(target);
        if (state.isAir()) { stopBreaking(z, target); return true; }
        
        float speed = Math.max(1.0f, z.getMainHandStack().getMiningSpeedMultiplier(state));
        int maxTime = Math.max(ModConfig.INSTANCE.breaking.breakMinTicks, (int)(20 / (speed * ModConfig.INSTANCE.breaking.breakSpeedMultiplier)));
        ((EntityAccessor)z).getWorld().setBlockBreakingInfo(z.getId(), target, (int)(((float)timer / maxTime) * 10));
        
        if (timer >= maxTime) {
            stopBreaking(z, target);
            ((EntityAccessor)z).getWorld().breakBlock(target, true);
            return true;
        }
        return false;
    }

    public static boolean shouldStop(ZombieEntity z, BlockPos lb) {
        return lb == null || ((EntityAccessor)z).getWorld().getBlockState(lb).isAir();
    }

    private static void stopBreaking(ZombieEntity z, BlockPos p) {
        ((EntityAccessor)z).getWorld().setBlockBreakingInfo(z.getId(), p, -1);
    }
}






