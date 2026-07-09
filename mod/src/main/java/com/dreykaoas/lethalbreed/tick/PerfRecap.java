package com.dreykaoas.lethalbreed.tick;

import com.dreykaoas.lethalbreed.config.domain.SchedulerConfig;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.LODLevel;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/**
 * Dev perf recap, emitted every {@code debugLogInterval} ticks (~5s). Active ONLY in a development
 * environment AND when {@code SchedulerConfig.debugLogInterval > 0} — so a built/published jar never logs.
 */
final class PerfRecap {
    private final ZombieRegistry registry;
    private final DimensionManager dimensions;

    private long aiNanosAccum = 0L; // our AI time accumulated over the perf-recap interval

    PerfRecap(ZombieRegistry registry, DimensionManager dimensions) {
        this.registry = registry;
        this.dimensions = dimensions;
    }

    /** Add this tick's measured AI time to the running accumulator. */
    void accumulate(long nanos) {
        aiNanosAccum += nanos;
    }

    void maybeLog(MinecraftServer server, long tickCounter) {
        int interval = SchedulerConfig.debugLogInterval;
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

        LethalBreed.LOGGER.info(String.format(
                "[LethalBreed][PERF] ai=%.2fms/tick | serverMSPT=%.2fms | zombies=%d (HIGH:%d MED:%d LOW:%d FROZEN:%d) highTgt=%d | blockOps=%d placed=%d dims=%d | mem=%d/%dMB",
                aiMs, serverMs, registry.size(), high, med, low, frozen, highWithTarget,
                pendingOps, placed, dimensions.contexts().size(), usedMb, maxMb));
        aiNanosAccum = 0L;
    }
}
