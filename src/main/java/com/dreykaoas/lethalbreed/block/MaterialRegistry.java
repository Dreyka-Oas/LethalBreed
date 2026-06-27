package com.dreykaoas.lethalbreed.block;

import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Decides whether a block may be broken by a zombie. */
public final class MaterialRegistry {
    private MaterialRegistry() {}

    public static boolean isBreakable(Level level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false; // never "break" liquids
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0f) {
            return false; // bedrock, barrier, etc.
        }
        return hardness <= LethalBreedConfig.breakMaxHardness;
    }
}
