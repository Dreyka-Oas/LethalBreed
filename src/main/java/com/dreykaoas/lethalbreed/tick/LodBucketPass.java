package com.dreykaoas.lethalbreed.tick;

import com.dreykaoas.lethalbreed.config.domain.SchedulerConfig;

import com.dreykaoas.lethalbreed.ai.LODManager;
import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.LODLevel;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * The staggered per-zombie pass: only zombies whose {@code bucketIndex} matches the current bucket
 * run their AI this tick. Reclassifies LOD, keeps the spatial grid fresh, applies sun burn, then
 * throttles AI by distance tier. Collects climbers/swimmers for the every-tick {@link EveryTickPass}.
 */
final class LodBucketPass {
    private final ZombieRegistry registry;
    private final DimensionManager dimensions;

    LodBucketPass(ZombieRegistry registry, DimensionManager dimensions) {
        this.registry = registry;
        this.dimensions = dimensions;
    }

    void run(MinecraftServer server, int currentBucket, Set<SmartZombie> climbers, Set<SmartZombie> swimmers) {
        // Same tickBuckets the scheduler used to derive currentBucket this tick, so membership is consistent.
        // Computing the bucket live (id % buckets) means a runtime tickBuckets change re-spreads every zombie
        // at once — no zombie is stranded on a now-out-of-range cached index.
        int buckets = Math.max(1, SchedulerConfig.tickBuckets);
        for (SmartZombie sz : registry.all()) {
            if (Math.floorMod(sz.id(), buckets) != currentBucket) {
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
            WorldAIContext ctx = dimensions.get(sz.dimension());
            // Keep FROZEN zombies in the spatial grid (their tick() — which inserts them — is skipped below)
            // so neighbour queries still find them: a Hurleur rallying idle zombies, a Soigneur healing them,
            // and sound propagation all target exactly these.
            ctx.spatialGrid().update(sz, sz.entity().blockPosition().getX(), sz.entity().blockPosition().getZ());
            // Daylight burn must apply even to idle/FROZEN zombies (whose full tick() below is skipped).
            sz.applySunBurn(level);
            if (lod == LODLevel.FROZEN) {
                continue;
            }
            // Distance-tier throttle: distant zombies run their AI less often.
            int divisor = 1;
            if (SchedulerConfig.throttleByLod) {
                divisor = switch (lod) {
                    case MEDIUM -> SchedulerConfig.lodMediumTickDivisor;
                    case LOW -> SchedulerConfig.lodLowTickDivisor;
                    default -> 1;
                };
            }
            if (!sz.dueThisActivation(divisor)) {
                continue;
            }

            sz.tick(level, ctx);
            if (sz.isClimbing()) {
                climbers.add(sz);
            }
            if (sz.isSwimming()) {
                swimmers.add(sz);
            }
        }
    }

    private void untrack(SmartZombie sz) {
        WorldAIContext ctx = dimensions.get(sz.dimension());
        ctx.spatialGrid().remove(sz);
        registry.remove(sz.id());
    }
}
