package oas.work.lethalbreed.ai.builder;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class VoidAnalyzer {
    public static boolean isDeep(World world, BlockPos pos) {
        int voidCount = 0;
        for (int i = 0; i < 4; i++) {
            var s = world.getBlockState(pos.down(i));
            if (s.isReplaceable() || s.isOf(Blocks.LAVA)) voidCount++;
            else break;
        }
        return voidCount >= 2;
    }
}
