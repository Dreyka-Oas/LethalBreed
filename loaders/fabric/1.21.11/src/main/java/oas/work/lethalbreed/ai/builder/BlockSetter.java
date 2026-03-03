/**
 * Project: Lethal Breed
 * Responsibility: Block Placement Utility
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.ai.builder;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockSetter {
    public static void placeDirt(World world, BlockPos pos) {
        if (PlacementValidator.canPlaceAt(world, pos)) {
            world.setBlockState(pos, Blocks.DIRT.getDefaultState());
        }
    }
}