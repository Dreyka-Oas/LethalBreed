/**
 * Project: Lethal Breed
 * Responsibility: Vector and Void Analysis for Building
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
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
