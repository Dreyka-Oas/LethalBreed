package com.dreykaoas.lethalbreed.spatial;

import com.dreykaoas.lethalbreed.entity.SmartZombie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 2D spatial hash over the XZ plane (one per dimension). Cells are {@code cellSize} blocks wide.
 * Used to answer radius queries cheaply (sound propagation, neighbour lookups) without scanning
 * every zombie.
 *
 * <p>Single-threaded for now (mutated from the server thread). When zombie ticks move off-thread,
 * reads will go through immutable snapshots instead.
 */
public final class SpatialGrid {
    private final int cellSize;
    private final Map<Long, List<SmartZombie>> cells = new HashMap<>();

    public SpatialGrid(int cellSize) {
        this.cellSize = Math.max(1, cellSize);
    }

    private long key(int blockX, int blockZ) {
        int cx = Math.floorDiv(blockX, cellSize);
        int cz = Math.floorDiv(blockZ, cellSize);
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }

    /** Re-bucket a zombie if it crossed a cell boundary. Returns the new cell key. */
    public long update(SmartZombie z, int blockX, int blockZ) {
        long newKey = key(blockX, blockZ);
        long oldKey = z.cellKey();
        if (z.inGrid() && oldKey == newKey) {
            return newKey;
        }
        if (z.inGrid()) {
            removeFrom(oldKey, z);
        }
        cells.computeIfAbsent(newKey, k -> new ArrayList<>(4)).add(z);
        z.setCell(newKey, true);
        return newKey;
    }

    public void remove(SmartZombie z) {
        if (z.inGrid()) {
            removeFrom(z.cellKey(), z);
            z.setCell(0L, false);
        }
    }

    private void removeFrom(long key, SmartZombie z) {
        List<SmartZombie> list = cells.get(key);
        if (list != null) {
            list.remove(z);
            if (list.isEmpty()) {
                cells.remove(key);
            }
        }
    }

    /** Collect zombies within {@code radius} blocks of (x,z). Cheap broad-phase + exact filter. */
    public List<SmartZombie> queryRadius(double x, double z, double radius) {
        List<SmartZombie> out = new ArrayList<>();
        int minCx = Math.floorDiv((int) Math.floor(x - radius), cellSize);
        int maxCx = Math.floorDiv((int) Math.floor(x + radius), cellSize);
        int minCz = Math.floorDiv((int) Math.floor(z - radius), cellSize);
        int maxCz = Math.floorDiv((int) Math.floor(z + radius), cellSize);
        double r2 = radius * radius;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                long k = (((long) cx) << 32) ^ (cz & 0xffffffffL);
                List<SmartZombie> list = cells.get(k);
                if (list == null) continue;
                for (SmartZombie sz : list) {
                    double dx = sz.x() - x;
                    double dz = sz.z() - z;
                    if (dx * dx + dz * dz <= r2) {
                        out.add(sz);
                    }
                }
            }
        }
        return out;
    }

    public int cellCount() {
        return cells.size();
    }

    public void clear() {
        cells.clear();
    }
}
