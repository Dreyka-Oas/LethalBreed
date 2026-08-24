package com.dreykaoas.lethalbreed.pack;

import com.dreykaoas.lethalbreed.config.domain.PackConfig;
import com.dreykaoas.lethalbreed.pack.runtime.PackLifecycle;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import java.util.List;

/**
 * The per-tick round over the packs: recentroid, dissolve the spent ones, try to merge the rest.
 *
 * <p>Only {@code packsPerTick} packs are visited per tick, resuming from a cursor, so the cost stays flat
 * however many packs exist. The visit order is sorted by id rather than by hash order: the merge below
 * mutates the map it iterates, and a stable order is what makes a merge sequence reproducible across a
 * reload instead of depending on where the ids happened to land.
 */
final class PackSweep {
    private PackSweep() {}

    /** @return the cursor to resume from next tick. */
    static int tick(PackManager manager, Long2ObjectMap<PackState> packs, List<PackState> ordered,
                    int cursor, long gameTime) {
        if (packs.isEmpty()) {
            return cursor;
        }
        ordered.clear();
        ordered.addAll(packs.values());
        ordered.sort((a, b) -> Long.compare(a.id, b.id));

        int visits = Math.min(Math.max(1, PackConfig.packsPerTick), ordered.size());
        for (int i = 0; i < visits; i++) {
            PackState pack = ordered.get(cursor++ % ordered.size());
            if (!PackConfig.packEnabled) {
                // Turned off at runtime. PackPass hands out no membership any more, so nothing else will
                // ever empty these or dissolve them: the ones already down to nobody would sit in the map,
                // and in the save, for the rest of the session. Only those are reaped; a pack still owed a
                // ghost or a detached member keeps its roster for when the option comes back on.
                if (pack.isEmpty()) {
                    manager.drop(pack.id);
                }
                continue;
            }
            PackLifecycle.recentroid(pack);
            if (PackLifecycle.dissolveIfSpent(pack, gameTime, manager)) {
                continue;
            }
            PackLifecycle.merge(pack, ordered, manager, manager::get);
        }
        return cursor % Math.max(1, ordered.size());
    }
}
