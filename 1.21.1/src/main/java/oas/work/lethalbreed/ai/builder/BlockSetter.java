package oas.work.lethalbreed.ai.builder;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import oas.work.lethalbreed.ai.TemporaryBlockTracker;

public class BlockSetter {
    public static void placeDirt(World world, BlockPos pos) {
        if (PlacementValidator.canPlaceAt(world, pos)) {
            world.setBlockState(pos, Blocks.DIRT.getDefaultState());
            if (world instanceof ServerWorld sw) {
                int dimensionId = world.getDimension().hashCode();
                TemporaryBlockTracker.track(pos, dimensionId);
            }
        }
    }
}



