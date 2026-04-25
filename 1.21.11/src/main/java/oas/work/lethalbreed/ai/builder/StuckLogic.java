package oas.work.lethalbreed.ai.builder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import oas.work.lethalbreed.mixin.EntityAccessor;

public class StuckLogic {
    public static BlockPos findBlocking(Zombie z, Vec3 tPos, BlockPos async) {
        BlockPos lb = async != null ? async : ObstructionAnalyzer.getStuckBlock(((EntityAccessor)z).getWorld(), z, tPos);
        if (lb == null && z.horizontalCollision) {
            lb = z.blockPosition().relative(z.getDirection());
            if (!ObstructionAnalyzer.isBlocking(((EntityAccessor)z).getWorld(), lb)) lb = lb.above();
        }
        return (lb != null && ObstructionAnalyzer.isBlocking(((EntityAccessor)z).getWorld(), lb)) ? lb : null;
    }
}









