package oas.dreyka.lethalbreed.ai.flowfield;

/**
 * Immutable per-dimension flow field over a rectangular XZ region. Each reachable cell stores its
 * cost-to-nearest-player and a unit step direction pointing downhill. What it takes to enter a cell,
 * break a block or bridge a gap, is already priced into that cost by {@link FlowFieldSnapshotBuilder}
 * and is not carried here. A zombie samples its own cell and the cell ahead in O(1).
 */
public final class FlowField {
    public static final short IMPASSABLE = Short.MAX_VALUE;

    private final int originX;
    private final int originZ;
    private final int width;
    private final int depth;
    private final short[] cost;
    private final byte[] dirX;
    private final byte[] dirZ;

    public FlowField(int originX, int originZ, int width, int depth,
                     short[] cost, byte[] dirX, byte[] dirZ) {
        this.originX = originX;
        this.originZ = originZ;
        this.width = width;
        this.depth = depth;
        this.cost = cost;
        this.dirX = dirX;
        this.dirZ = dirZ;
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

    /** Cost-to-nearest-goal at world (wx,wz), or {@link #IMPASSABLE} when the cell is outside the field.
     *  Exposed for diagnostics and the CPU/GPU parity self-test (the field is otherwise direction-sampled). */
    public int costAt(int wx, int wz) {
        int cx = wx - originX;
        int cz = wz - originZ;
        if (cx < 0 || cx >= width || cz < 0 || cz >= depth) {
            return IMPASSABLE;
        }
        return cost[index(cx, cz)];
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }
}
