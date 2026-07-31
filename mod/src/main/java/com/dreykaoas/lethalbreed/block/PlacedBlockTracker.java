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
 * block position. The world-facing side only; every lifetime rule (expiry, abandon threshold, crack ramp)
 * lives in {@link PlacedBlockPolicy} so it can be unit-tested without Minecraft on the classpath.
 */
public final class PlacedBlockTracker {
    private static final class State extends CrackingBlock {
        long placedAt;
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
        long lifetime = CombatMoveConfig.placedBlockLifetimeTicks; // floored at 1 inside PlacedBlockPolicy
        Iterator<Map.Entry<Long, State>> it = placed.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, State> e = it.next();
            State s = e.getValue();
            BlockPos p = BlockPos.of(e.getKey());
            long age = now - s.placedAt;
            // Never force a chunk load from here. Level.getBlockState() resolves through
            // getChunk(x, z, FULL, /* requireChunk */ true), which on a ServerLevel cache miss does
            // addTicket + managedBlock + join — a synchronous stall of the server thread, once per tracked
            // position per tick, with up to 12000 of them per dimension (audit #3). Same guard
            // CellClassifier:31 already uses. An unloaded placement is held, not dropped: we can neither
            // read nor destroy the block while its chunk is gone, so an over-age entry is resolved by the
            // loaded path below the moment the chunk comes back, however much later that is. Only a very
            // long continuous unload (PlacedBlockPolicy.ABANDON_FACTOR lifetimes) makes the tracker give up.
            if (!level.isLoaded(p)) {
                // Chunk gone: decide nothing here. We can neither read the block state nor destroy it,
                // so we hold the entry and handle it when the chunk returns — even hours later.
                if (PlacedBlockPolicy.abandoned(age, lifetime)) {
                    it.remove(); // unloaded this long straight: give up rather than grow without bound
                }
                continue;
            }
            BlockState bs = level.getBlockState(p);
            if (bs.getBlock() != Blocks.DIRT) {
                // Block already gone/replaced → clear any cracks and stop tracking it.
                s.clearCracks(level, p);
                it.remove();
                continue;
            }
            if (PlacedBlockPolicy.expired(age, lifetime)) {
                s.clearCracks(level, p);
                // Same effect as breaking by hand (particles + sound) but NO drop.
                level.destroyBlock(p, false, null, 512);
                it.remove();
                continue;
            }
            // Ramp the cracking overlay across the block's lifetime (0..9).
            s.showStage(level, p, PlacedBlockPolicy.crackStage(age, lifetime));
        }
    }

    public int trackedCount() {
        return placed.size();
    }
}
