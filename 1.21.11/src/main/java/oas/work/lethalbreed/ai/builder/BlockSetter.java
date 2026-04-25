package oas.work.lethalbreed.ai.builder;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import oas.work.lethalbreed.ai.TemporaryBlockTracker;

public class BlockSetter {
    public static void placeDirt(Level world, BlockPos pos) {
        if (PlacementValidator.canPlaceAt(world, pos)) {
            world.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            if (!world.isClientSide()) {
                int dimensionId = world.dimension().hashCode();
                TemporaryBlockTracker.track(pos, dimensionId);
            }
        }
    }
}