package oas.work.lethalbreed.ai.builder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.state.BlockState;
import oas.work.lethalbreed.config.ModConfig;
import oas.work.lethalbreed.mixin.EntityAccessor;

public class BreakAction {
    public static boolean tick(Zombie z, BlockPos target, int timer) {
        if (target == null) return true;
        z.getNavigation().stop();
        z.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        BlockState state = z.getLevel().getBlockState(target);
        if (state.isAir()) { stopBreaking(z, target); return true; }
        
        float speed = Math.max(1.0f, z.getMainHandItem().getDestroySpeed(state));
        int maxTime = Math.max(ModConfig.INSTANCE.breaking.breakMinTicks, (int)(20 / (speed * ModConfig.INSTANCE.breaking.breakSpeedMultiplier)));
        ((EntityAccessor)z).getWorld().destroyBlockProgress(z.getId(), target, (int)(((float)timer / maxTime) * 10));
        
        if (timer >= maxTime) {
            stopBreaking(z, target);
            ((EntityAccessor)z).getWorld().destroyBlock(target, true);
            return true;
        }
        return false;
    }

    public static boolean shouldStop(Zombie z, BlockPos lb) {
        return lb == null || ((EntityAccessor)z).getWorld().getBlockState(lb).isAir();
    }

    private static void stopBreaking(Zombie z, BlockPos p) {
        ((EntityAccessor)z).getWorld().destroyBlockProgress(z.getId(), p, -1);
    }
}







