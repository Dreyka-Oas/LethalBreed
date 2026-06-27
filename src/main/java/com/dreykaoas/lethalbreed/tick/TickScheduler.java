package com.dreykaoas.lethalbreed.tick;

import com.dreykaoas.lethalbreed.LethalBreedMod;
import com.dreykaoas.lethalbreed.ai.LODLevel;
import com.dreykaoas.lethalbreed.ai.LODManager;
import com.dreykaoas.lethalbreed.command.DevSpawnScheduler;
import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Staggers zombie updates across {@code tickBuckets} server ticks so a large population spreads its
 * work instead of spiking every tick. Each server tick processes exactly one bucket.
 *
 * <p>Phase 1: everything runs on the server thread. Phase 5 moves the per-zombie work to a worker
 * pool, keeping world writes on the server thread.
 */
public final class TickScheduler {
    private final ZombieRegistry registry;
    private final DimensionManager dimensions;

    private long tickCounter = 0L;
    private long aiNanosAccum = 0L; // our AI time accumulated over the perf-recap interval
    private final Set<SmartZombie> climbers = new HashSet<>(); // zombies mid jump-pillar, ticked every tick
    private final Set<SmartZombie> swimmers = new HashSet<>(); // zombies in water, ticked every tick (rise/dive)

    public TickScheduler(ZombieRegistry registry, DimensionManager dimensions) {
        this.registry = registry;
        this.dimensions = dimensions;
    }

    public void onServerTick(MinecraftServer server) {
        long t0 = System.nanoTime();
        int buckets = Math.max(1, LethalBreedConfig.tickBuckets);
        int currentBucket = (int) Math.floorMod(tickCounter, buckets);

        enforceWorldRules(server);
        DevSpawnScheduler.tick(server);
        processSound(server);

        for (SmartZombie sz : registry.all()) {
            if (sz.bucketIndex() != currentBucket) {
                continue;
            }
            if (!sz.isValid()) {
                untrack(sz);
                continue;
            }

            ServerLevel level = server.getLevel(sz.dimension());
            if (level == null) {
                continue;
            }

            // Reclassify every activation so LOD + nearest-player (used for pillaring) stay fresh for
            // ALL buckets — a global tick%interval would only ever align with bucket 0.
            LODManager.classify(sz, level);
            LODLevel lod = sz.lod();
            if (lod == LODLevel.FROZEN) {
                continue;
            }
            // Distance-tier throttle: distant zombies run their AI less often.
            int divisor = 1;
            if (LethalBreedConfig.throttleByLod) {
                divisor = switch (lod) {
                    case MEDIUM -> LethalBreedConfig.lodMediumTickDivisor;
                    case LOW -> LethalBreedConfig.lodLowTickDivisor;
                    default -> 1;
                };
            }
            if (!sz.dueThisActivation(divisor)) {
                continue;
            }

            WorldAIContext ctx = dimensions.get(sz.dimension());
            sz.tick(level, ctx);
            if (sz.isClimbing()) {
                climbers.add(sz);
            }
            if (sz.isSwimming()) {
                swimmers.add(sz);
            }
        }

        processClimbers(server);
        processSwimmers(server);
        drainBlockOps(server);
        aiNanosAccum += System.nanoTime() - t0;
        maybeLog(server);
        tickCounter++;
    }

    /** Recompute each active dimension's flow field once per tick (throttled inside the manager). */
    private void recomputeFlowFields(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, WorldAIContext> e : dimensions.contexts().entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            if (level != null) {
                e.getValue().flowFieldManager().tick(level, tickCounter);
            }
        }
    }

    /** Keep the overworld at constant daytime and clear weather (config-gated). */
    private void enforceWorldRules(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        if (ow == null) {
            return;
        }
        if (LethalBreedConfig.forceDayTime) {
            ow.setDayTime(LethalBreedConfig.forcedDayTime);
        }
        if (LethalBreedConfig.clearWeather && ow.isRaining()) {
            ow.setWeatherParameters(6000, 0, false, false);
        }
    }

    /** Finish in-progress jump-pillars every tick (not bucket-gated) so the jump+place looks natural. */
    private void processClimbers(MinecraftServer server) {
        if (climbers.isEmpty()) {
            return;
        }
        Iterator<SmartZombie> it = climbers.iterator();
        while (it.hasNext()) {
            SmartZombie sz = it.next();
            if (!sz.isValid() || !sz.isClimbing()) {
                it.remove();
                continue;
            }
            ServerLevel level = server.getLevel(sz.dimension());
            if (level == null) {
                it.remove();
                continue;
            }
            sz.climbStep(level, dimensions.get(sz.dimension()));
            if (!sz.isClimbing()) {
                it.remove();
            }
        }
    }

    /** Drive rise/dive for in-water zombies every tick (not bucket-gated) so the dive impulse beats the
     *  per-tick FloatGoal lift. */
    private void processSwimmers(MinecraftServer server) {
        if (swimmers.isEmpty()) {
            return;
        }
        Iterator<SmartZombie> it = swimmers.iterator();
        while (it.hasNext()) {
            SmartZombie sz = it.next();
            if (!sz.isValid() || !sz.isSwimming()) {
                it.remove();
                continue;
            }
            ServerLevel level = server.getLevel(sz.dimension());
            if (level == null) {
                it.remove();
                continue;
            }
            sz.swimStep(level, dimensions.get(sz.dimension()));
            if (!sz.isSwimming()) {
                it.remove();
            }
        }
    }

    /** Emit player/loud sounds and distribute them to nearby zombies, per dimension. */
    private void processSound(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, WorldAIContext> e : dimensions.contexts().entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            if (level == null) {
                continue;
            }
            WorldAIContext ctx = e.getValue();
            ctx.soundBus().tickPlayers(level);
            ctx.soundBus().process(ctx.spatialGrid());
        }
    }

    /** Apply queued world mutations under budget and expire old zombie-placed blocks. */
    private void drainBlockOps(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, WorldAIContext> e : dimensions.contexts().entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            if (level == null) {
                continue;
            }
            WorldAIContext ctx = e.getValue();
            ctx.blockOps().drain(level, ctx.placedBlocks(), tickCounter);
            ctx.breakManager().tick(level, tickCounter);
            ctx.placedBlocks().tick(level, tickCounter);
        }
    }

    private void untrack(SmartZombie sz) {
        WorldAIContext ctx = dimensions.get(sz.dimension());
        ctx.spatialGrid().remove(sz);
        registry.remove(sz.id());
    }

    /**
     * Dev perf recap, emitted every {@code debugLogInterval} ticks (~5s). Active ONLY in a development
     * environment AND once {@code /lethalspawn} has turned it on — so a built/published jar never logs.
     */
    private void maybeLog(MinecraftServer server) {
        int interval = LethalBreedConfig.debugLogInterval;
        if (interval <= 0 || tickCounter % interval != 0) {
            return;
        }
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            aiNanosAccum = 0L;
            return; // perf recap is dev-only — gone in a built/published jar
        }

        int high = 0, med = 0, low = 0, frozen = 0, highWithTarget = 0;
        for (SmartZombie sz : registry.all()) {
            switch (sz.lod()) {
                case HIGH -> high++;
                case MEDIUM -> med++;
                case LOW -> low++;
                case FROZEN -> frozen++;
            }
            if (sz.lod() == LODLevel.HIGH && sz.hasTarget()) {
                highWithTarget++;
            }
        }
        int pendingOps = 0, placed = 0;
        for (WorldAIContext ctx : dimensions.contexts().values()) {
            pendingOps += ctx.blockOps().pendingCount();
            placed += ctx.placedBlocks().trackedCount();
        }
        double aiMs = aiNanosAccum / (double) interval / 1_000_000.0;
        double serverMs = server.getAverageTickTimeNanos() / 1_000_000.0;
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576L;
        long maxMb = rt.maxMemory() / 1_048_576L;

        LethalBreedMod.LOGGER.info(String.format(
                "[LethalBreed][PERF] ai=%.2fms/tick | serverMSPT=%.2fms | zombies=%d (HIGH:%d MED:%d LOW:%d FROZEN:%d) highTgt=%d | blockOps=%d placed=%d dims=%d | mem=%d/%dMB",
                aiMs, serverMs, registry.size(), high, med, low, frozen, highWithTarget,
                pendingOps, placed, dimensions.contexts().size(), usedMb, maxMb));
        aiNanosAccum = 0L;
    }
}
