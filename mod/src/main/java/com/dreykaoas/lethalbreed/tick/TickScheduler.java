package com.dreykaoas.lethalbreed.tick;

import com.dreykaoas.lethalbreed.config.domain.engine.SchedulerConfig;

import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieRegistry;
import com.dreykaoas.lethalbreed.probe.DevProbe;
import net.minecraft.server.MinecraftServer;

import java.util.HashSet;
import java.util.Set;

/**
 * Staggers zombie updates across {@code tickBuckets} server ticks so a large population spreads its
 * work instead of spiking every tick. Each server tick processes exactly one bucket.
 *
 * <p>This class is the orchestrator: it owns the per-tick state and drives the split passes in a
 * fixed order — world rules + sound, then the bucketed {@link LodBucketPass}, then the every-tick
 * climb/swim {@link EveryTickPass}, then {@link WorldMaintenance#drainBlockOps drains}, then the
 * end-of-tick report through {@link DevProbe}. The scheduling math and pass order are deliberate; the
 * helpers are pure splits.
 */
public final class TickScheduler {
    private final ZombieRegistry registry;
    private final LodBucketPass bucketPass;
    private final EveryTickPass everyTickPass;
    private final WorldMaintenance world;

    private long tickCounter = 0L;
    private final Set<SmartZombie> climbers = new HashSet<>(); // zombies mid jump-pillar, ticked every tick
    private final Set<SmartZombie> swimmers = new HashSet<>(); // zombies in water, ticked every tick (rise/dive)

    public TickScheduler(ZombieRegistry registry, DimensionManager dimensions) {
        this.registry = registry;
        this.bucketPass = new LodBucketPass(registry, dimensions);
        this.everyTickPass = new EveryTickPass(dimensions);
        this.world = new WorldMaintenance(dimensions);
    }

    public void onServerTick(MinecraftServer server) {
        boolean probing = DevProbe.on();
        long t0 = probing ? System.nanoTime() : 0L;
        // Auto-scale: pick the bucket count so each tick processes ~autoScaleBucketLoad zombies, instead of a
        // fixed tickBuckets. buckets is computed once here and handed to the pass so membership stays consistent.
        int buckets;
        if (SchedulerConfig.autoScaleBuckets) {
            int load = Math.max(1, SchedulerConfig.autoScaleBucketLoad);
            buckets = Math.max(1, (registry.size() + load - 1) / load);
        } else {
            buckets = Math.max(1, SchedulerConfig.tickBuckets);
        }
        int currentBucket = (int) Math.floorMod(tickCounter, buckets);

        world.enforceWorldRules(server);
        com.dreykaoas.lethalbreed.phase.PhaseManager.get().tick(server);
        com.dreykaoas.lethalbreed.effect.ContaminationManager.tick(server);
        // Every tick, not bucketed: a puddle shrinks and re-doses on wall-clock cadence, so routing it through
        // the LOD buckets would tie its lifetime to a performance knob — the exact coupling the Bombeur's fuse
        // had to be rescued from.
        com.dreykaoas.lethalbreed.special.runtime.GorePuddles.tick(server);
        world.refreshTargetIndex(server); // must precede the bucket pass, which queries it
        world.processSound(server);
        // Before the bucket pass, for the same reason processSound is: the waypoint it plants is read by
        // LODManager.classify in that very pass.
        world.tickPacks(server, server.overworld().getGameTime());
        world.recomputeFlowFields(server, tickCounter);

        bucketPass.run(server, buckets, currentBucket, climbers, swimmers);

        everyTickPass.processClimbers(server, climbers);
        everyTickPass.processSwimmers(server, swimmers);
        world.drainBlockOps(server, tickCounter);
        if (probing) {
            DevProbe.sink.tickEnd(server, tickCounter, System.nanoTime() - t0);
        }
        tickCounter++;
    }

    /**
     * SERVER_STOPPED: drop the every-tick worklists. {@code climbers}/{@code swimmers} hold {@link SmartZombie}
     * references from the world that just closed; this scheduler is a JVM-lived {@code static} in
     * {@code LethalBreedMod}, so without this the old {@code ServerLevel} stays pinned until the NEXT server's
     * first tick prunes them via {@code EveryTickPass.drive} — i.e. across the whole menu/loading window, at
     * peak memory (audit #20). {@code tickCounter} resets so a fresh world starts its stagger from 0.
     */
    public void reset() {
        climbers.clear();
        swimmers.clear();
        tickCounter = 0L;
    }
}
