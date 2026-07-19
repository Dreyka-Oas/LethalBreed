package com.dreykaoas.lethalbreed.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Shared crumbling-overlay bookkeeping for a tracked block: a synthetic breaker id and the last crack stage
 * pushed, so both the progressive {@link BreakManager} and the lifetime {@link PlacedBlockTracker} ramp the
 * vanilla 0→9 overlay the exact same way — pushing a packet only when the stage actually changes, and clearing
 * the cracks the same way when the block is done.
 */
abstract class CrackingBlock {
    int breakerId;
    int lastStage = -1;

    /** Clamp an already-scaled tenths value (progress×10 / age×10÷lifetime) to a 0..9 crack stage. */
    static int stage(double tenths) {
        return (int) Math.max(0, Math.min(9, tenths));
    }

    /** Push the crack overlay only when the stage actually changed (avoids per-tick packet spam). */
    void showStage(Level level, BlockPos pos, int stage) {
        if (stage != lastStage) {
            level.destroyBlockProgress(breakerId, pos, stage);
            lastStage = stage;
        }
    }

    /** Clear any crack overlay for this block. */
    void clearCracks(Level level, BlockPos pos) {
        level.destroyBlockProgress(breakerId, pos, -1);
    }
}
