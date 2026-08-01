package com.dreykaoas.lethalbreed.ai.flowfield;

/**
 * Immutable world snapshot for one flow-field solve. Produced on the server thread by
 * {@link FlowFieldSnapshotBuilder} (classifying cells into cost arrays) and consumed off-thread by
 * {@link BellmanFordSolver} (the CPU backup) or the GPU solver — neither of which touch Minecraft.
 */
public final class Snapshot {
    final int originX, originZ, width, depth, focusY;
    final boolean[] passable;
    final int[] extraCost;
    final byte[] flags;
    final int[] seedCells;

    Snapshot(int originX, int originZ, int width, int depth, int focusY,
             boolean[] passable, int[] extraCost, byte[] flags, int[] seedCells) {
        this.originX = originX;
        this.originZ = originZ;
        this.width = width;
        this.depth = depth;
        this.focusY = focusY;
        this.passable = passable;
        this.extraCost = extraCost;
        this.flags = flags;
        this.seedCells = seedCells;
    }

    /** A flat, fully passable {@code side×side} field with a single corner seed at (0,0) — the clean synthetic
     *  workload shared by the calibration bench and the self-test (which then carves its own wall).
     *
     *  <p>Public because the self-test now lives in the {@code dev} source set. This is the ONLY way to obtain
     *  a {@link Snapshot} without a world: the constructor stays package-private, so no external caller can
     *  fabricate an arbitrary (possibly inconsistent) snapshot — they can only ask for this one canonical
     *  synthetic field and then mutate the arrays the already-public accessors hand out. */
    public static Snapshot openSquare(int side) {
        int n = side * side;
        boolean[] passable = new boolean[n];
        java.util.Arrays.fill(passable, true);
        return new Snapshot(0, 0, side, side, 64, passable, new int[n], new byte[n], new int[]{0});
    }

    public int originX() { return originX; }
    public int originZ() { return originZ; }
    public int width() { return width; }
    public int depth() { return depth; }
    public int focusY() { return focusY; }
    public boolean[] passable() { return passable; }
    public boolean[] walk() { return passable; } // GPU path compatibility
    public int[] extraCost() { return extraCost; }
    public byte[] flags() { return flags; }
    public int[] seedCells() { return seedCells; }
}
