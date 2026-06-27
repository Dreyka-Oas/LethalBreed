package com.dreykaoas.lethalbreed.ai.flowfield;

import com.dreykaoas.lethalbreed.block.MaterialRegistry;
import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * CPU flow-field builder. Block-aware: cells are PASSABLE (free), BREAKABLE (traversable at a cost,
 * flagged to break through), BUILDABLE (a gap traversable at a cost, flagged to bridge), or
 * IMPASSABLE. Dijkstra over these costs lets the optimal path go THROUGH a breakable wall or OVER a
 * gap when that beats a long detour — which is what makes zombies break and bridge.
 *
 * <ol>
 *   <li>{@link #snapshot} reads the world on the server thread into immutable arrays.</li>
 *   <li>{@link #compute} runs Dijkstra over that snapshot on a worker thread (no MC access).</li>
 * </ol>
 */
public final class CpuFlowField {
    private CpuFlowField() {}

    private static final int ORTH = 10;
    private static final int DIAG = 14;
    private static final int[] NDX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] NDZ = {0, 0, 1, -1, 1, -1, 1, -1};

    // cell classification
    private static final byte PASSABLE = 0;
    private static final byte BREAKABLE = 1;
    private static final byte BUILDABLE = 2;
    private static final byte IMPASSABLE = 3;

    public static final class Snapshot {
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

    /** SERVER THREAD: classify cells + seeds. {@code players} should already be filtered to targets. */
    public static Snapshot snapshot(ServerLevel level, List<ServerPlayer> players) {
        int margin = LethalBreedConfig.flowMargin;
        int maxGrid = LethalBreedConfig.flowMaxGrid;
        int vtol = LethalBreedConfig.flowVerticalTolerance;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        long sumY = 0;
        for (ServerPlayer p : players) {
            BlockPos bp = p.blockPosition();
            minX = Math.min(minX, bp.getX());
            maxX = Math.max(maxX, bp.getX());
            minZ = Math.min(minZ, bp.getZ());
            maxZ = Math.max(maxZ, bp.getZ());
            sumY += bp.getY();
        }
        int focusY = (int) (sumY / players.size());

        minX -= margin; maxX += margin;
        minZ -= margin; maxZ += margin;
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        if (width > maxGrid) {
            int cx = (minX + maxX) / 2;
            minX = cx - maxGrid / 2;
            width = maxGrid;
        }
        if (depth > maxGrid) {
            int cz = (minZ + maxZ) / 2;
            minZ = cz - maxGrid / 2;
            depth = maxGrid;
        }

        int n = width * depth;
        boolean[] passable = new boolean[n];
        int[] extraCost = new int[n];
        byte[] flags = new byte[n];
        int breakCost = LethalBreedConfig.flowBreakCost;
        int buildCost = LethalBreedConfig.flowBuildCost;

        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int cx = 0; cx < width; cx++) {
            int wx = minX + cx;
            for (int cz = 0; cz < depth; cz++) {
                int wz = minZ + cz;
                int i = cx * depth + cz;
                byte type = classify(level, m, wx, wz, focusY, vtol);
                switch (type) {
                    case PASSABLE -> { passable[i] = true; }
                    case BREAKABLE -> { passable[i] = true; extraCost[i] = breakCost; flags[i] = FlowField.FLAG_BREAK; }
                    case BUILDABLE -> { passable[i] = true; extraCost[i] = buildCost; flags[i] = FlowField.FLAG_BUILD; }
                    default -> { passable[i] = false; }
                }
            }
        }

        List<Integer> seeds = new ArrayList<>(players.size());
        for (ServerPlayer p : players) {
            int cx = p.blockPosition().getX() - minX;
            int cz = p.blockPosition().getZ() - minZ;
            if (cx < 0 || cx >= width || cz < 0 || cz >= depth) {
                continue;
            }
            int i = cx * depth + cz;
            passable[i] = true;
            extraCost[i] = 0;
            flags[i] = FlowField.FLAG_NONE;
            seeds.add(i);
        }
        int[] seedCells = new int[seeds.size()];
        for (int k = 0; k < seedCells.length; k++) {
            seedCells[k] = seeds.get(k);
        }
        return new Snapshot(minX, minZ, width, depth, focusY, passable, extraCost, flags, seedCells);
    }

    /** WORKER THREAD: pure Dijkstra over the snapshot. No Minecraft access. */
    public static FlowField compute(Snapshot s) {
        int width = s.width, depth = s.depth, n = width * depth;
        boolean[] passable = s.passable;
        int[] extra = s.extraCost;
        short[] cost = new short[n];
        Arrays.fill(cost, FlowField.IMPASSABLE);
        byte[] dirX = new byte[n];
        byte[] dirZ = new byte[n];

        PriorityQueue<Long> pq = new PriorityQueue<>();
        for (int seed : s.seedCells) {
            cost[seed] = 0;
            pq.add(pack(0, seed));
        }

        while (!pq.isEmpty()) {
            long top = pq.poll();
            int c = (int) (top >>> 32);
            int i = (int) (top & 0xffffffffL);
            if (c > cost[i]) {
                continue;
            }
            int cx = i / depth;
            int cz = i % depth;
            for (int k = 0; k < 8; k++) {
                int nx = cx + NDX[k];
                int nz = cz + NDZ[k];
                if (nx < 0 || nx >= width || nz < 0 || nz >= depth) {
                    continue;
                }
                int ni = nx * depth + nz;
                if (!passable[ni]) {
                    continue;
                }
                boolean diag = NDX[k] != 0 && NDZ[k] != 0;
                if (diag && (!passable[cx * depth + nz] || !passable[nx * depth + cz])) {
                    continue; // no corner cutting
                }
                int nc = c + (diag ? DIAG : ORTH) + extra[ni];
                if (nc < cost[ni]) {
                    cost[ni] = (short) Math.min(nc, FlowField.IMPASSABLE - 1);
                    pq.add(pack(nc, ni));
                }
            }
        }

        for (int cx = 0; cx < width; cx++) {
            for (int cz = 0; cz < depth; cz++) {
                int i = cx * depth + cz;
                if (cost[i] >= FlowField.IMPASSABLE || cost[i] == 0) {
                    continue;
                }
                int bestCost = cost[i];
                int bdx = 0, bdz = 0;
                for (int k = 0; k < 8; k++) {
                    int nx = cx + NDX[k];
                    int nz = cz + NDZ[k];
                    if (nx < 0 || nx >= width || nz < 0 || nz >= depth) {
                        continue;
                    }
                    int ni = nx * depth + nz;
                    if (cost[ni] >= FlowField.IMPASSABLE) {
                        continue;
                    }
                    boolean diag = NDX[k] != 0 && NDZ[k] != 0;
                    if (diag && (!passable[cx * depth + nz] || !passable[nx * depth + cz])) {
                        continue;
                    }
                    if (cost[ni] < bestCost) {
                        bestCost = cost[ni];
                        bdx = NDX[k];
                        bdz = NDZ[k];
                    }
                }
                dirX[i] = (byte) bdx;
                dirZ[i] = (byte) bdz;
            }
        }

        return new FlowField(s.originX, s.originZ, width, depth, s.focusY, cost, dirX, dirZ, s.flags);
    }

    private static long pack(int cost, int index) {
        return ((long) cost << 32) | (index & 0xffffffffL);
    }

    /**
     * Classify a column at the focus plane: PASSABLE if there is a standable spot in the vertical
     * window; else BREAKABLE if a breakable wall blocks the focus plane; else BUILDABLE if there is a
     * gap (clear feet/head, no ground); else IMPASSABLE.
     */
    private static byte classify(ServerLevel level, BlockPos.MutableBlockPos m,
                                 int wx, int wz, int focusY, int vtol) {
        // Standable anywhere in the window?
        for (int y = focusY + vtol; y >= focusY - vtol; y--) {
            m.set(wx, y, wz);
            if (!level.isLoaded(m)) {
                continue;
            }
            boolean feet = !level.getBlockState(m).blocksMotion();
            m.set(wx, y + 1, wz);
            boolean head = !level.getBlockState(m).blocksMotion();
            m.set(wx, y - 1, wz);
            boolean ground = level.getBlockState(m).blocksMotion();
            if (feet && head && ground) {
                return PASSABLE;
            }
        }

        // Not standable: examine the focus plane (where the zombie would walk).
        m.set(wx, focusY, wz);
        if (!level.isLoaded(m)) {
            return IMPASSABLE;
        }
        BlockState feetState = level.getBlockState(m);
        boolean feetSolid = feetState.blocksMotion();
        m.set(wx, focusY + 1, wz);
        BlockState headState = level.getBlockState(m);
        boolean headSolid = headState.blocksMotion();
        m.set(wx, focusY - 1, wz);
        boolean groundSolid = level.getBlockState(m).blocksMotion();

        if (feetSolid || headSolid) {
            // A wall. Breakable only if every solid layer is breakable.
            boolean feetOk = !feetSolid || MaterialRegistry.isBreakable(level, new BlockPos(wx, focusY, wz), feetState);
            boolean headOk = !headSolid || MaterialRegistry.isBreakable(level, new BlockPos(wx, focusY + 1, wz), headState);
            return (feetOk && headOk) ? BREAKABLE : IMPASSABLE;
        }
        if (!groundSolid) {
            // Clear feet+head but nothing to stand on within tolerance → bridge it.
            return BUILDABLE;
        }
        return IMPASSABLE;
    }
}
