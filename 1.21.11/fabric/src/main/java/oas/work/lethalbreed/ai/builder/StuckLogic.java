package oas.work.lethalbreed.ai.builder;

import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import oas.work.lethalbreed.mixin.EntityAccessor;

public class StuckLogic {
    public static BlockPos findBlocking(ZombieEntity z, Vec3d tPos, BlockPos async) {
        BlockPos lb = async != null ? async : ObstructionAnalyzer.getStuckBlock(((EntityAccessor)z).getWorld(), z, tPos);
        if (lb == null && z.horizontalCollision) {
            lb = z.getBlockPos().offset(z.getHorizontalFacing());
            if (!ObstructionAnalyzer.isBlocking(((EntityAccessor)z).getWorld(), lb)) lb = lb.up();
        }
        return (lb != null && ObstructionAnalyzer.isBlocking(((EntityAccessor)z).getWorld(), lb)) ? lb : null;
    }
}
