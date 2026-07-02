package com.dreykaoas.lethalbreed.block;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Tracks dirt placed by zombies and removes it (without dropping an item) after a lifetime, so the world is
 * not permanently reshaped by bridging/pillaring. As each block ages toward its lifetime it shows the vanilla
 * cracking overlay (stage 0→9) so players see it crumbling over time before it finally pops. Keyed by packed
 * block position.
 */
public final class PlacedBlockTracker {
    private static final class State {
        long placedAt;
        int breakerId;
        int lastStage = -1;
    }

    private final HashMap<Long, State> placed = new HashMap<>();
    private int breakerSeq = 200_000; // synthetic breaker ids, distinct from BreakManager's range

    public void record(BlockPos pos, long tick) {
        State s = new State();
        s.placedAt = tick;
        s.breakerId = breakerSeq++;
        placed.put(pos.asLong(), s);
    }

    /** Advance crumbling cracks and remove expired placements. Called once per tick per dimension. */
    public void tick(Level level, long now) {
        if (placed.isEmpty()) {
            return;
        }
        long lifetime = Math.max(1, CombatMoveConfig.placedBlockLifetimeTicks);
        Iterator<Map.Entry<Long, State>> it = placed.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, State> e = it.next();
            State s = e.getValue();
            BlockPos p = BlockPos.of(e.getKey());
            BlockState bs = level.getBlockState(p);
            if (bs.getBlock() != Blocks.DIRT) {
                // Block already gone/replaced → clear any cracks and stop tracking it.
                level.destroyBlockProgress(s.breakerId, p, -1);
                it.remove();
                continue;
            }
            long age = now - s.placedAt;
            if (age >= lifetime) {
                level.destroyBlockProgress(s.breakerId, p, -1);
                // Same effect as breaking by hand (particles + sound) but NO drop.
                level.destroyBlock(p, false, null, 512);
                it.remove();
                continue;
            }
            // Ramp the cracking overlay across the block's lifetime (0..9).
            int stage = (int) Math.max(0, Math.min(9, (age * 10L) / lifetime));
            if (stage != s.lastStage) {
                level.destroyBlockProgress(s.breakerId, p, stage);
                s.lastStage = stage;
            }
        }
    }

    public int trackedCount() {
        return placed.size();
    }
}
