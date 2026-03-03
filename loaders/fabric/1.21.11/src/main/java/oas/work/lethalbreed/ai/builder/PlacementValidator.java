/**
 * Project: Lethal Breed
 * Responsibility: Block Placement Validation
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.ai.builder;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;

public class PlacementValidator {
    public static boolean canPlaceAt(World world, BlockPos pos) {
        if (world.isOutOfHeightLimit(pos)) return false;
        BlockState state = world.getBlockState(pos);
        
        // Placement is allowed if:
        // 1. It is a replaceable block (air, grass, etc.)
        // 2. It is not a full cube (slab, stair, carpet, etc.)
        return state.isReplaceable() || !state.isFullCube(world, pos);
    }
}