package com.dreykaoas.lethalbreed.ai.flowfield;

/**
 * Immutable per-dimension flow field over a rectangular XZ region. Each reachable cell stores its
 * cost-to-nearest-player, a unit step direction pointing downhill, and a flag describing what is
 * needed to ENTER the cell (nothing, break a block, or bridge a gap). A zombie samples its own cell
 * and the cell ahead in O(1).
 */
public final class FlowField {
    public static final short IMPASSABLE = Short.MAX_VALUE;

    public static final byte FLAG_NONE = 0;
    public static final byte FLAG_BREAK = 1;
    public static final byte FLAG_BUILD = 2;

    private final int originX;
    private final int originZ;
    private final int width;
    private final int depth;
    private final int focusY;
    private final short[] cost;
    private final byte[] dirX;
    private final byte[] dirZ;
    private final byte[] flags;

    public FlowField(int originX, int originZ, int width, int depth, int focusY,
                     short[] cost, byte[] dirX, byte[] dirZ, byte[] flags) {
        this.originX = originX;
        this.originZ = originZ;
        this.width = width;
        this.depth = depth;
        this.focusY = focusY;
        this.cost = cost;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.flags = flags;
    }

    public int focusY() {
        return focusY;
    }

    private int index(int cellX, int cellZ) {
        return cellX * depth + cellZ;
    }

    /**
     * Writes the downhill step direction at world (wx,wz) into {@code out} (length 2) and returns
     * true, or returns false if the cell is outside the field, impassable, or already at a goal.
     */
    public boolean sampleInto(int wx, int wz, int[] out) {
        int cx = wx - originX;
        int cz = wz - originZ;
        if (cx < 0 || cx >= width || cz < 0 || cz >= depth) {
            return false;
        }
        int i = index(cx, cz);
        if (cost[i] >= IMPASSABLE) {
            return false;
        }
        byte dx = dirX[i];
        byte dz = dirZ[i];
        if (dx == 0 && dz == 0) {
            return false; // goal cell or dead spot
        }
        out[0] = dx;
        out[1] = dz;
        return true;
    }

    /** Flag for the cell at world (wx,wz): break/build needed to enter it, or none/out-of-bounds. */
    public byte flagAt(int wx, int wz) {
        int cx = wx - originX;
        int cz = wz - originZ;
        if (cx < 0 || cx >= width || cz < 0 || cz >= depth) {
            return FLAG_NONE;
        }
        return flags[index(cx, cz)];
    }

    public boolean contains(int wx, int wz) {
        int cx = wx - originX;
        int cz = wz - originZ;
        return cx >= 0 && cx < width && cz >= 0 && cz < depth;
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }
}
