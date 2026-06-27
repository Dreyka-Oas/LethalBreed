package com.dreykaoas.lethalbreed.ai.flowfield;

import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
import com.dreykaoas.lethalbreed.gpu.GpuFlowField;
import com.dreykaoas.lethalbreed.util.Players;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the active flow field for one dimension. The world snapshot is captured on the server thread;
 * the Dijkstra solve runs on a shared daemon pool and the result is swapped in atomically. Zombie
 * ticks read {@link #active()} lock-free.
 */
public final class FlowFieldManager {
    /** Shared low-priority daemon pool for all dimensions' flow-field solves. */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "LethalBreed-FlowField");
        t.setDaemon(true);
        t.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
        return t;
    });

    private final AtomicReference<FlowField> active = new AtomicReference<>();
    private final AtomicBoolean computing = new AtomicBoolean(false);
    private long lastComputeTick = Long.MIN_VALUE;

    public FlowField active() {
        return active.get();
    }

    /** SERVER THREAD: throttled. Snapshots the world here, solves off-thread. */
    public void tick(ServerLevel level, long serverTick) {
        int interval = Math.max(1, LethalBreedConfig.flowRecomputeInterval);
        if (serverTick - lastComputeTick < interval) {
            return;
        }
        if (computing.get()) {
            return; // previous solve still running; skip this cycle
        }

        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (Players.isTargetable(p)) {
                players.add(p);
            }
        }
        if (players.isEmpty()) {
            active.set(null);
            lastComputeTick = serverTick;
            return;
        }

        lastComputeTick = serverTick;
        CpuFlowField.Snapshot snapshot = CpuFlowField.snapshot(level, players); // main thread read
        computing.set(true);
        POOL.submit(() -> {
            try {
                active.set(GpuFlowField.compute(snapshot)); // GPU if enabled+available, else CPU
            } finally {
                computing.set(false);
            }
        });
    }

    public void clear() {
        active.set(null);
        lastComputeTick = Long.MIN_VALUE;
    }
}
