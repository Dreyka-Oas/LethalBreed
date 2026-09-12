package oas.dreyka.lethalbreed.sound;

import oas.dreyka.lethalbreed.config.domain.TargetingConfig;

import oas.dreyka.lethalbreed.entity.SmartZombie;
import oas.dreyka.lethalbreed.spatial.SpatialGrid;
import oas.dreyka.lethalbreed.spatial.TargetIndex;
import oas.dreyka.lethalbreed.util.Players;
import oas.dreyka.lethalbreed.util.target.Perception;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

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
    // How often the prey sweep (tickEntities) actually runs, now read from soundScanIntervalTicks. A moving
    // creature's noise refreshes a zombie's short-term memory (~10 s window), so scanning every Nth tick
    // instead of every tick is imperceptible for pursuit while cutting that sweep by N×. Player footsteps
    // (tickPlayers) and event distribution (process) still run EVERY tick. Only the O(prey) creature scan is
    // throttled.
    private static int entityScanInterval() {
        return Math.max(1, TargetingConfig.soundScanIntervalTicks);
    }

    // event = {x, y, z, radius}
    private final List<double[]> events = new ArrayList<>();
    /** Reused across every event of every tick so process() does not allocate a fresh ArrayList per queued
     *  sound event. Safe to share because the list is consumed fully before the next queryRadiusInto. */
    private final List<SmartZombie> nearScratch = new ArrayList<>();
    // playerId -> last {x, y, z}
    private final Map<Integer, double[]> lastPlayerPos = new HashMap<>();
    private int entityScanCounter = 0;

    /** Queue a sound at a world position with a hearing radius. */
    public void emit(double x, double y, double z, double radius) {
        if (!TargetingConfig.soundEnabled) {
            return;
        }
        events.add(new double[]{x, y, z, radius});
    }

    /** Emit footstep sounds for players that moved this tick (skipping sneaking players). */
    public void tickPlayers(ServerLevel level) {
        if (!TargetingConfig.soundEnabled) {
            return;
        }
        double threshold = TargetingConfig.soundMoveThreshold;
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
                double radius = TargetingConfig.soundBaseRadius
                        * Math.min(TargetingConfig.soundStepRadiusMax, 1.0 + dist);
                emit(x, y, z, radius);
            }
            prev[0] = x; prev[1] = y; prev[2] = z;
        }
    }

    /** Emit noise for NON-PLAYER living entities that are making sound this tick (walking, acting, hurt), so
     *  zombies hear villagers/animals/mobs move behind walls the same way they hear players. Players are handled
     *  by {@link #tickPlayers} (their server-side delta is unreliable, so that path uses positional delta);
     *  every other creature has reliable {@code getDeltaMovement}, so {@link Perception#isAudible} is enough.
     *  A loud action (arm swing) carries {@code ×soundLoudMultiplier}, mirroring the acquisition hearing rule. */
    public void tickEntities(TargetIndex index) {
        if (!TargetingConfig.soundEnabled) {
            return;
        }
        // Throttle the sweep (see entityScanInterval). Cheap early-out on the off ticks.
        if ((entityScanCounter++ % entityScanInterval()) != 0) {
            return;
        }
        double base = TargetingConfig.soundBaseRadius;
        double loud = base * Math.max(1.0, TargetingConfig.soundLoudMultiplier);
        // The prey index IS this method's filter: TargetIndex.indexable holds exactly the living entities
        // that are neither player (covered by tickPlayers) nor zombie (never hunted by their own kind), and
        // the scheduler refreshes it on the line above the one that calls us. Walking the level instead meant
        // visiting the whole horde, every dropped item and every projectile to reject them one by one.
        for (LivingEntity e : index.all()) {
            if (Perception.isAudible(e)) {
                emit(e.getX(), e.getY(), e.getZ(), e.swinging ? loud : base);
            }
        }
    }

    /** Distribute queued events to nearby zombies, then clear them for the next tick. A heard sound is fed
     *  into the zombie's SHORT-TERM MEMORY (the same last-known-position system used when a live target slips
     *  out of sight+sound): it keeps the zombie's LOD active and makes it pursue the sound's location for
     *  {@code targetMemoryTicks} (~10 s) instead of forgetting after a single tick. A live entity target
     *  always overrides. {@code gameTime} stamps the memory expiry. */
    public void process(SpatialGrid grid, long gameTime) {
        if (events.isEmpty()) {
            return;
        }
        boolean useMemory = TargetingConfig.targetMemoryTicks > 0;
        for (double[] e : events) {
            List<SmartZombie> near = grid.queryRadiusInto(nearScratch, e[0], e[1], e[2], e[3]);
            for (SmartZombie z : near) {
                // Every zombie in earshot is ROUSED by the noise: notifyHeardSound (re-)arms its day alert so an
                // awake one keeps hunting, and a sleeping one starts waking (after a short delay) to investigate
                // this exact spot. A silent, non-attacking player emits nothing, so it never trips this: stealth.
                z.mood().notifyHeardSound(gameTime, e[0], e[1], e[2]);
                if (z.entity().getTarget() != null) {
                    continue; // already chasing a player directly
                }
                if (useMemory) {
                    // Remember where the sound came from; LodManager's memory branch then pursues it (digging/
                    // descending toward the spot) and forgets on arrival or expiry. Persists focus past 1 tick.
                    z.pursuit().rememberTarget(e[0], e[1], e[2], gameTime + TargetingConfig.targetMemoryTicks);
                } else {
                    z.pursuit().setSoundTarget(e[0], e[1], e[2]); // legacy path when memory is disabled
                }
            }
        }
        events.clear();
    }

}
