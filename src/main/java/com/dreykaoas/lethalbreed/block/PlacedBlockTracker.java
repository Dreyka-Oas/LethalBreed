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
 * Tracks dirt placed by zombies and removes it (without dropping an item) after a lifetime, so the
 * world is not permanently reshaped by bridging/pillaring. Keyed by packed block position.
 */
public final class PlacedBlockTracker {
    private final HashMap<Long, Long> placedAtTick = new HashMap<>();

    public void record(BlockPos pos, long tick) {
        placedAtTick.put(pos.asLong(), tick);
    }

    /** Remove expired placements. Called once per tick per dimension on the server thread. */
    public void tick(Level level, long now) {
        if (placedAtTick.isEmpty()) {
            return;
        }
        long lifetime = CombatMoveConfig.placedBlockLifetimeTicks;
        Iterator<Map.Entry<Long, Long>> it = placedAtTick.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Long> e = it.next();
            if (now - e.getValue() >= lifetime) {
                BlockPos p = BlockPos.of(e.getKey());
                BlockState s = level.getBlockState(p);
                if (s.getBlock() == Blocks.DIRT) {
                    // Same effect as breaking by hand (particles + sound) but NO drop.
                    level.destroyBlock(p, false, null, 512);
                }
                it.remove();
            }
        }
    }

    public int trackedCount() {
        return placedAtTick.size();
    }
}
