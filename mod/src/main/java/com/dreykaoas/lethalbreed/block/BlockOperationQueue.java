package com.dreykaoas.lethalbreed.block;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;

/**
 * Per-dimension queue of pending block <i>placements</i> (bridge spans / pillar supports). Zombies
 * enqueue place requests during their tick; the scheduler drains them on the server thread under a
 * per-tick budget. A position set deduplicates requests so a crowd targeting one cell enqueues it once.
 * Breaks do NOT go through here — they are progressive and handled by {@link BreakManager}.
 *
 * <p>Phase 3 is server-thread only. When zombie ticks move off-thread (Phase 5) this becomes a
 * concurrent queue and the dedup set a {@code ConcurrentHashMap.newKeySet()}.
 */
public final class BlockOperationQueue {
    private final ArrayDeque<BlockPos> places = new ArrayDeque<>();
    private final HashSet<Long> pending = new HashSet<>();

    public void enqueuePlace(BlockPos pos) {
        if (!CombatMoveConfig.blockOpsEnabled) {
            return; // master toggle: no bridging/pillar placements
        }
        if (places.size() >= CombatMoveConfig.blockOpsQueueCap) {
            return;
        }
        if (pending.add(pos.asLong())) {
            places.add(pos.immutable());
        }
    }

    /** Apply up to the per-tick budget of placements. */
    public void drain(Level level, PlacedBlockTracker tracker, long tick) {
        int budget = CombatMoveConfig.blockOpsPerTick;

        while (budget > 0 && !places.isEmpty()) {
            BlockPos p = places.poll();
            pending.remove(p.asLong());
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !s.blocksMotion()) { // only fill air / passable vegetation
                level.setBlock(p, Blocks.DIRT.defaultBlockState(), 3);
                tracker.record(p, tick);
                budget--;
            }
        }
    }

    public int pendingCount() {
        return places.size();
    }
}
