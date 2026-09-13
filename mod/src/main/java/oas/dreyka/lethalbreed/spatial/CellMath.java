package oas.dreyka.lethalbreed.spatial;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import java.util.List;

/**
 * Shared flat-XZ spatial-grid math: cell-key packing and world-coordinate-to-cell-index conversion. Used
 * by both {@link SpatialGrid} (the zombie index) and {@link TargetIndex} (the prey index) so the two
 * independently-tuned indices hash cells identically, even though they use different cell sizes.
 */
public final class CellMath {
    private CellMath() {}

    /** Pack a cell coordinate pair into one long key. The single source of truth for cell hashing. */
    public static long packKey(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }

    /** Floor a world coordinate down to its cell index for the given cell size. */
    public static int floorCell(double coord, int cellSize) {
        return Math.floorDiv((int) Math.floor(coord), cellSize);
    }

    /** Drop {@code entity} from its bucket under {@code key}, and drop the bucket itself once empty so the
     *  map never accumulates dead keys. Same removal shape for every flat-XZ index regardless of payload
     *  type, which is why it lives here rather than being copied by each index. */
    public static <T> void removeFrom(Long2ObjectMap<List<T>> cells, long key, T entity) {
        List<T> list = cells.get(key);
        if (list != null) {
            list.remove(entity);
            if (list.isEmpty()) {
                cells.remove(key);
            }
        }
    }
}
