package oas.work.lethalbreed.ai.builder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import oas.work.lethalbreed.ai.TemporaryBlockTracker;

public class BlockSetter {
    public static void placeDirt(Level world, BlockPos pos) {
        if (PlacementValidator.canPlaceAt(world, pos)) {
            world.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
            if (world instanceof ServerLevel sw) {
                int dimensionId = world.dimensionType().hashCode();
                TemporaryBlockTracker.track(pos, dimensionId);
            }
        }
    }
}






