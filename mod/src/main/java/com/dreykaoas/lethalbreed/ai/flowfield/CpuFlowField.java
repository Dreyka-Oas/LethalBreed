package com.dreykaoas.lethalbreed.ai.flowfield;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * CPU flow-field facade. Block-aware Dijkstra/Bellman-Ford pathfinding split into two stages:
 *
 * <ol>
 *   <li>{@link #snapshot} reads the world on the server thread into an immutable {@link Snapshot}
 *       (delegates to {@link FlowFieldSnapshotBuilder}).</li>
 *   <li>{@link #compute} runs parallel Bellman-Ford over that snapshot on a worker thread, no MC
 *       access (delegates to {@link BellmanFordSolver}).</li>
 * </ol>
 */
public final class CpuFlowField {
    private CpuFlowField() {}

    /** SERVER THREAD: classify cells + seeds. {@code players} should already be filtered to targets. */
    public static Snapshot snapshot(ServerLevel level, List<ServerPlayer> players) {
        return FlowFieldSnapshotBuilder.snapshot(level, players);
    }

    /** WORKER THREAD: solve the snapshot with parallel Bellman-Ford. No Minecraft access. */
    public static FlowField compute(Snapshot s) {
        return BellmanFordSolver.compute(s);
    }
}
