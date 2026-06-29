package com.dreykaoas.lethalbreed.ai.flowfield;

import com.dreykaoas.lethalbreed.config.domain.FlowConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * SERVER THREAD: reads the world into an immutable {@link Snapshot}. Cells are PASSABLE (free),
 * BREAKABLE (traversable at a cost, flagged to break through), BUILDABLE (a gap traversable at a
 * cost, flagged to bridge), or IMPASSABLE (per {@link CellClassifier}). The cost arrays let the
 * off-thread solve route a path THROUGH a breakable wall or OVER a gap when that beats a long detour
 * — which is what makes zombies break and bridge.
 */
final class FlowFieldSnapshotBuilder {
    private FlowFieldSnapshotBuilder() {}

    /** Classify cells + seeds. {@code players} should already be filtered to targets. */
    static Snapshot snapshot(ServerLevel level, List<ServerPlayer> players) {
        int margin = FlowConfig.flowMargin;
        int maxGrid = FlowConfig.flowMaxGrid;
        int vtol = FlowConfig.flowVerticalTolerance;

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
        int breakCost = FlowConfig.flowBreakCost;
        int buildCost = FlowConfig.flowBuildCost;

        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int cx = 0; cx < width; cx++) {
            int wx = minX + cx;
            for (int cz = 0; cz < depth; cz++) {
                int wz = minZ + cz;
                int i = cx * depth + cz;
                byte type = CellClassifier.classify(level, m, wx, wz, focusY, vtol);
                switch (type) {
                    case CellClassifier.PASSABLE -> { passable[i] = true; }
                    case CellClassifier.BREAKABLE -> { passable[i] = true; extraCost[i] = breakCost; flags[i] = FlowField.FLAG_BREAK; }
                    case CellClassifier.BUILDABLE -> { passable[i] = true; extraCost[i] = buildCost; flags[i] = FlowField.FLAG_BUILD; }
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
}
