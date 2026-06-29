package com.dreykaoas.lethalbreed.tick;

import com.dreykaoas.lethalbreed.config.domain.SchedulerConfig;

import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieRegistry;
import net.minecraft.server.MinecraftServer;

import java.util.HashSet;
import java.util.Set;

/**
 * Staggers zombie updates across {@code tickBuckets} server ticks so a large population spreads its
 * work instead of spiking every tick. Each server tick processes exactly one bucket.
 *
 * <p>This class is the orchestrator: it owns the per-tick state and drives the split passes in a
 * fixed order — world rules + sound, then the bucketed {@link LodBucketPass}, then the every-tick
 * climb/swim {@link EveryTickPass}, then {@link WorldMaintenance#drainBlockOps drains}, then the dev
 * {@link PerfRecap}. The scheduling math and pass order are deliberate; the helpers are pure splits.
 */
public final class TickScheduler {
    private final LodBucketPass bucketPass;
    private final EveryTickPass everyTickPass;
    private final WorldMaintenance world;
    private final PerfRecap perfRecap;

    private long tickCounter = 0L;
    private final Set<SmartZombie> climbers = new HashSet<>(); // zombies mid jump-pillar, ticked every tick
    private final Set<SmartZombie> swimmers = new HashSet<>(); // zombies in water, ticked every tick (rise/dive)

    public TickScheduler(ZombieRegistry registry, DimensionManager dimensions) {
        this.bucketPass = new LodBucketPass(registry, dimensions);
        this.everyTickPass = new EveryTickPass(dimensions);
        this.world = new WorldMaintenance(dimensions);
        this.perfRecap = new PerfRecap(registry, dimensions);
    }

    public void onServerTick(MinecraftServer server) {
        long t0 = System.nanoTime();
        int buckets = Math.max(1, SchedulerConfig.tickBuckets);
        int currentBucket = (int) Math.floorMod(tickCounter, buckets);

        world.enforceWorldRules(server);
        com.dreykaoas.lethalbreed.phase.PhaseManager.get().tick(server);
        com.dreykaoas.lethalbreed.effect.ContaminationManager.tick(server);
        world.processSound(server);

        bucketPass.run(server, currentBucket, climbers, swimmers);

        everyTickPass.processClimbers(server, climbers);
        everyTickPass.processSwimmers(server, swimmers);
        world.drainBlockOps(server, tickCounter);
        perfRecap.accumulate(System.nanoTime() - t0);
        perfRecap.maybeLog(server, tickCounter);
        tickCounter++;
    }
}
