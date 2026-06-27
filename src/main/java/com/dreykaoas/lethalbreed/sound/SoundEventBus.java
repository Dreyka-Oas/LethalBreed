package com.dreykaoas.lethalbreed.sound;

import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.spatial.SpatialGrid;
import com.dreykaoas.lethalbreed.util.Players;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-dimension sound perception. Collects sound events for the current tick (player footsteps from
 * movement, loud events like block breaks) and distributes them to nearby zombies via the spatial
 * grid, setting their sound target. Server-thread only.
 */
public final class SoundEventBus {
    // event = {x, y, z, radius}
    private final List<double[]> events = new ArrayList<>();
    // playerId -> last {x, y, z}
    private final Map<Integer, double[]> lastPlayerPos = new HashMap<>();

    /** Queue a sound at a world position with a hearing radius. */
    public void emit(double x, double y, double z, double radius) {
        if (!LethalBreedConfig.soundEnabled) {
            return;
        }
        events.add(new double[]{x, y, z, radius});
    }

    /** Emit footstep sounds for players that moved this tick (skipping sneaking players). */
    public void tickPlayers(ServerLevel level) {
        if (!LethalBreedConfig.soundEnabled) {
            return;
        }
        double threshold = LethalBreedConfig.soundMoveThreshold;
        for (ServerPlayer p : level.players()) {
            if (!Players.isTargetable(p)) {
                continue; // creative/spectator make no noise (config)
            }
            double x = p.getX(), y = p.getY(), z = p.getZ();
            double[] prev = lastPlayerPos.get(p.getId());
            if (prev == null) {
                lastPlayerPos.put(p.getId(), new double[]{x, y, z});
                continue;
            }
            double dx = x - prev[0], dy = y - prev[1], dz = z - prev[2];
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist >= threshold && !p.isCrouching()) {
                double radius = LethalBreedConfig.soundBaseRadius * Math.min(2.0, 1.0 + dist);
                emit(x, y, z, radius);
            }
            prev[0] = x; prev[1] = y; prev[2] = z;
        }
    }

    /** Distribute queued events to nearby zombies, then clear them for the next tick. */
    public void process(SpatialGrid grid) {
        if (events.isEmpty()) {
            return;
        }
        for (double[] e : events) {
            List<SmartZombie> near = grid.queryRadius(e[0], e[2], e[3]);
            for (SmartZombie z : near) {
                if (z.entity().getTarget() != null) {
                    continue; // already chasing a player directly
                }
                z.setSoundTarget(e[0], e[1], e[2]);
            }
        }
        events.clear();
    }

    public void clear() {
        events.clear();
        lastPlayerPos.clear();
    }
}
