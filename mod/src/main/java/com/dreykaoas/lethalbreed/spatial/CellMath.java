package com.dreykaoas.lethalbreed.spatial;

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
}
