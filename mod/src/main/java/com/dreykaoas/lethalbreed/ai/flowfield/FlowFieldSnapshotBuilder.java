package com.dreykaoas.lethalbreed.ai.flowfield;

import com.dreykaoas.lethalbreed.config.domain.engine.FlowConfig;
import com.dreykaoas.lethalbreed.probe.DevProbe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * SERVER THREAD: reads the world into an immutable {@link Snapshot}. Cells are PASSABLE (free),
 * BREAKABLE (traversable at the break cost), BUILDABLE (a gap traversable at the build cost), or
 * IMPASSABLE (per {@link CellClassifier}). The classification survives only as that per-cell cost:
 * it lets the off-thread solve route a path THROUGH a breakable wall or OVER a gap when that beats
 * a long detour. That is what makes zombies break and bridge.
 */
public final class FlowFieldSnapshotBuilder {
    private FlowFieldSnapshotBuilder() {}

    /**
     * The window one solve covers: origin, width and depth, margin included and clamped to {@code maxGrid}.
     * Separated from {@link #snapshot} so the geometry can be tested without a server.
     *
     * <p>When the clamp bites, the window recentres on the player closest to the group's centre of mass, not
     * on the middle of their bounding box: two players further apart than the grid put that middle in a window
     * holding neither, and a window with no player in it seeds nothing, leaving every cell IMPASSABLE silently.
     */
    public static int[] window(int[] xs, int[] zs, int margin, int maxGrid) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        long sumX = 0, sumZ = 0;
        for (int k = 0; k < xs.length; k++) {
            minX = Math.min(minX, xs[k]);
            maxX = Math.max(maxX, xs[k]);
            minZ = Math.min(minZ, zs[k]);
            maxZ = Math.max(maxZ, zs[k]);
            sumX += xs[k];
            sumZ += zs[k];
        }
        minX -= margin; maxX += margin;
        minZ -= margin; maxZ += margin;
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        if (width <= maxGrid && depth <= maxGrid) {
            return new int[]{minX, minZ, width, depth};
        }
        int anchor = 0;
        long best = Long.MAX_VALUE;
        long cxAvg = sumX / xs.length, czAvg = sumZ / zs.length;
        for (int k = 0; k < xs.length; k++) {
            long dx = xs[k] - cxAvg, dz = zs[k] - czAvg;
            long d = dx * dx + dz * dz;
            if (d < best) {
                best = d;
                anchor = k;
            }
        }
        if (width > maxGrid) {
            minX = xs[anchor] - maxGrid / 2;
            width = maxGrid;
        }
        if (depth > maxGrid) {
            minZ = zs[anchor] - maxGrid / 2;
            depth = maxGrid;
        }
        return new int[]{minX, minZ, width, depth};
    }

    /** Classify cells + seeds. {@code players} should already be filtered to targets. */
    public static Snapshot snapshot(ServerLevel level, List<ServerPlayer> players) {
        int margin = FlowConfig.flowMargin;
        int maxGrid = FlowConfig.flowMaxGrid;
        int vtol = FlowConfig.flowVerticalTolerance;

        int[] xs = new int[players.size()];
        int[] zs = new int[players.size()];
        long sumY = 0;
        for (int k = 0; k < players.size(); k++) {
            BlockPos bp = players.get(k).blockPosition();
            xs[k] = bp.getX();
            zs[k] = bp.getZ();
            sumY += bp.getY();
        }
        int focusY = (int) (sumY / players.size());

        int[] w = window(xs, zs, margin, maxGrid);
        int minX = w[0], minZ = w[1], width = w[2], depth = w[3];

        int n = width * depth;
        boolean[] passable = new boolean[n];
        int[] extraCost = new int[n];
        int breakCost = FlowConfig.flowBreakCost;
        int buildCost = FlowConfig.flowBuildCost;

        boolean prof = DevProbe.on();
        long t0 = prof ? System.nanoTime() : 0L;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int cx = 0; cx < width; cx++) {
            int wx = minX + cx;
            int lastChunkZ = Integer.MIN_VALUE;
            net.minecraft.world.level.chunk.ChunkAccess chunk = null;
            for (int cz = 0; cz < depth; cz++) {
                int wz = minZ + cz;
                int chunkZ = wz >> 4;
                if (chunkZ != lastChunkZ) {
                    // 16 consecutive columns share one chunk; resolving it per column repeated the lookup
                    // 16x over. getChunk(x, z, FULL, false), never force a load: an absent chunk is
                    // IMPASSABLE, the same conclusion CellClassifier's isLoaded guard already reaches.
                    chunk = level.getChunk(wx >> 4, chunkZ,
                            net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                    lastChunkZ = chunkZ;
                }
                int i = cx * depth + cz;
                byte type = (chunk == null)
                        ? CellClassifier.IMPASSABLE
                        : CellClassifier.classify(level, chunk, m, wx, wz, focusY, vtol);
                switch (type) {
                    case CellClassifier.PASSABLE -> { passable[i] = true; }
                    case CellClassifier.BREAKABLE -> { passable[i] = true; extraCost[i] = breakCost; }
                    case CellClassifier.BUILDABLE -> { passable[i] = true; extraCost[i] = buildCost; }
                    default -> { passable[i] = false; }
                }
            }
        }
        if (prof) {
            DevProbe.sink.stage(DevProbe.FLOWSNAP, System.nanoTime() - t0);
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
            seeds.add(i);
        }
        int[] seedCells = new int[seeds.size()];
        for (int k = 0; k < seedCells.length; k++) {
            seedCells[k] = seeds.get(k);
        }
        return new Snapshot(minX, minZ, width, depth, focusY, passable, extraCost, seedCells);
    }
}
