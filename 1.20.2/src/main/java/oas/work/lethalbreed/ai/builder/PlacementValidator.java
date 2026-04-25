package oas.work.lethalbreed.ai.builder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class PlacementValidator {
    public static boolean canPlaceAt(Level world, BlockPos pos) {
        if (world.isOutsideBuildHeight(pos)) return false;
        BlockState state = world.getBlockState(pos);
        
        return state.isAir() || !state.isRedstoneConductor(world, pos);
    }
}






