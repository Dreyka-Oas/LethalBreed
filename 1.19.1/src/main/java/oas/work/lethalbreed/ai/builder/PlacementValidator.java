package oas.work.lethalbreed.ai.builder;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;

public class PlacementValidator {
    public static boolean canPlaceAt(World world, BlockPos pos) {
        if (world.isOutOfHeightLimit(pos)) return false;
        BlockState state = world.getBlockState(pos);
        
        return state.getMaterial().isReplaceable() || !state.isFullCube(world, pos);
    }
}







