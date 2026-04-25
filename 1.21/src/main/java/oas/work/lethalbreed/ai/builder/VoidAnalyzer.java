package oas.work.lethalbreed.ai.builder;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class VoidAnalyzer {
    public static boolean isDeep(Level world, BlockPos pos) {
        int voidCount = 0;
        for (int i = 0; i < 4; i++) {
            var s = world.getBlockState(pos.below(i));
            if (s.canBeReplaced() || s.is(Blocks.LAVA)) voidCount++;
            else break;
        }
        return voidCount >= 2;
    }
}