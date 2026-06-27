package com.dreykaoas.lethalbreed.block;

import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;

/**
 * Per-dimension queue of pending world mutations. Zombies enqueue break/place requests during their
 * tick; the scheduler drains them on the server thread under a per-tick budget (breaks first). A
 * position set deduplicates requests so a crowd hitting one block enqueues it once.
 *
 * <p>Phase 3 is server-thread only. When zombie ticks move off-thread (Phase 5) this becomes a
 * concurrent queue and the dedup set a {@code ConcurrentHashMap.newKeySet()}.
 */
public final class BlockOperationQueue {
    private final ArrayDeque<BlockPos> breaks = new ArrayDeque<>();
    private final ArrayDeque<BlockPos> places = new ArrayDeque<>();
    private final HashSet<Long> pending = new HashSet<>();

    public void enqueueBreak(BlockPos pos) {
        add(breaks, pos);
    }

    public void enqueuePlace(BlockPos pos) {
        add(places, pos);
    }

    private void add(ArrayDeque<BlockPos> q, BlockPos pos) {
        if (breaks.size() + places.size() >= LethalBreedConfig.blockOpsQueueCap) {
            return;
        }
        if (pending.add(pos.asLong())) {
            q.add(pos.immutable());
        }
    }

    /** Apply up to the per-tick budget. Breaks are spent first, then placements. */
    public void drain(Level level, PlacedBlockTracker tracker, long tick) {
        int budget = LethalBreedConfig.blockOpsPerTick;

        while (budget > 0 && !breaks.isEmpty()) {
            BlockPos p = breaks.poll();
            pending.remove(p.asLong());
            BlockState s = level.getBlockState(p);
            if (MaterialRegistry.isBreakable(level, p, s)) {
                level.destroyBlock(p, true, null, 512); // drop items + break effects (vanilla)
                budget--;
            }
        }

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
        return breaks.size() + places.size();
    }
}
