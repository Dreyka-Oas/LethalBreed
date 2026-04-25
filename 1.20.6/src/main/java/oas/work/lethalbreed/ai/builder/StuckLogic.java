package oas.work.lethalbreed.ai.builder;

import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

public class StuckLogic {
    public static BlockPos findBlocking(Zombie z, Vec3 tPos, BlockPos async) {
        Level world = z.level();
        BlockPos lb = async != null ? async : ObstructionAnalyzer.getStuckBlock(world, z, tPos);
        if (lb == null && z.horizontalCollision) {
            lb = z.blockPosition().relative(z.getDirection());
            if (!ObstructionAnalyzer.isBlocking(world, lb)) lb = lb.above();
        }
        return (lb != null && ObstructionAnalyzer.isBlocking(world, lb)) ? lb : null;
    }
}