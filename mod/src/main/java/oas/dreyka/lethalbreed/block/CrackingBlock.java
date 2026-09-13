package oas.dreyka.lethalbreed.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Shared crumbling-overlay bookkeeping for a tracked block: a synthetic breaker id and the last crack stage
 * pushed, so both the progressive {@link BreakManager} and the lifetime {@link PlacedBlockTracker} ramp the
 * vanilla 0→9 overlay the exact same way, pushing a packet only when the stage actually changes, and clearing
 * the cracks the same way when the block is done.
 */
abstract class CrackingBlock {
    int breakerId;
    int lastStage = -1;

    /**
     * Push the crack overlay only when the stage actually changed (avoids per-tick packet spam).
     *
     * <p>The position is frozen before it leaves: ClientboundBlockDestructionPacket keeps the instance it is
     * given and serialises it later, on the netty event loop, so a caller sweeping its blocks with one reused
     * cursor would have every packet read whatever position the sweep had reached by then. A caller that
     * already holds an immutable position pays nothing, {@code immutable()} returns itself.
     */
    void showStage(Level level, BlockPos pos, int stage) {
        if (stage != lastStage) {
            level.destroyBlockProgress(breakerId, pos.immutable(), stage);
            lastStage = stage;
        }
    }

    /** Clear any crack overlay for this block. Freezes the position for the reason given on {@link #showStage}. */
    void clearCracks(Level level, BlockPos pos) {
        level.destroyBlockProgress(breakerId, pos.immutable(), -1);
    }
}
