package com.dreykaoas.lethalbreed.spatial;

import com.dreykaoas.lethalbreed.config.domain.SchedulerConfig;
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
    private final Map<Long, List<SmartZombie>> cells = new HashMap<>();

    public SpatialGrid() {
    }

    /** Cell width read live from config, so editing spatialCellSize at runtime takes effect: keys computed
     *  with the new size diverge from each zombie's stored cellKey, so the next {@link #update} re-buckets it.
     *  The whole grid migrates within one bucket cycle (tickBuckets ticks); queries are correct once migrated. */
    private int cellSize() {
        return Math.max(1, SchedulerConfig.spatialCellSize);
    }

    private long key(int blockX, int blockZ) {
        int cell = cellSize();
        int cx = Math.floorDiv(blockX, cell);
        int cz = Math.floorDiv(blockZ, cell);
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }

    /** Re-bucket a zombie if it crossed a cell boundary. Returns the new cell key. */
    public long update(SmartZombie z, int blockX, int blockZ) {
        long newKey = key(blockX, blockZ);
        long oldKey = z.pursuit().cellKey();
        if (z.pursuit().inGrid() && oldKey == newKey) {
            return newKey;
        }
        if (z.pursuit().inGrid()) {
            removeFrom(oldKey, z);
        }
        cells.computeIfAbsent(newKey, k -> new ArrayList<>(4)).add(z);
        z.pursuit().setCell(newKey, true);
        return newKey;
    }

    public void remove(SmartZombie z) {
        if (z.pursuit().inGrid()) {
            removeFrom(z.pursuit().cellKey(), z);
            z.pursuit().setCell(0L, false);
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

    /** Collect zombies within {@code radius} blocks of (x,z), ignoring vertical distance (column query). */
    public List<SmartZombie> queryRadius(double x, double z, double radius) {
        return queryRadius(x, Double.NaN, z, radius);
    }

    /** Collect zombies within {@code radius} blocks of (x,z) and within {@link SchedulerConfig#spatialVerticalLimit}
     *  blocks vertically of {@code y}. The grid is flat XZ, so without the Y filter a zombie far above/below in the
     *  same column counts as "near"; passing a real {@code y} (and a limit > 0) drops those. Pass {@code y = NaN}
     *  or set the limit to 0 to keep the legacy column behaviour. Cheap broad-phase + exact filter. */
    public List<SmartZombie> queryRadius(double x, double y, double z, double radius) {
        List<SmartZombie> out = new ArrayList<>();
        int cell = cellSize();
        int minCx = Math.floorDiv((int) Math.floor(x - radius), cell);
        int maxCx = Math.floorDiv((int) Math.floor(x + radius), cell);
        int minCz = Math.floorDiv((int) Math.floor(z - radius), cell);
        int maxCz = Math.floorDiv((int) Math.floor(z + radius), cell);
        double r2 = radius * radius;
        double vlim = SchedulerConfig.spatialVerticalLimit;
        boolean checkY = vlim > 0.0 && !Double.isNaN(y);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                long k = (((long) cx) << 32) ^ (cz & 0xffffffffL);
                List<SmartZombie> list = cells.get(k);
                if (list == null) continue;
                for (SmartZombie sz : list) {
                    double dx = sz.x() - x;
                    double dz = sz.z() - z;
                    if (dx * dx + dz * dz <= r2 && (!checkY || Math.abs(sz.y() - y) <= vlim)) {
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
