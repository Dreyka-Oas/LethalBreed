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
import net.minecraft.world.entity.player.Player;

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

    // Rotated each run() so the frozen-reclassify skip staggers WHICH frozen zombies refresh on a given
    // activation instead of always the same id-residue set.
    private long frozenRound = 0L;

    void run(MinecraftServer server, int buckets, int currentBucket, Set<SmartZombie> climbers, Set<SmartZombie> swimmers) {
        // buckets is supplied by the scheduler (the same value it used to derive currentBucket), so membership
        // stays consistent even when autoScaleBuckets recomputes it from population each tick. Computing the
        // bucket live (id % buckets) means a count change re-spreads every zombie at once — none stranded.
        int frozenDiv = Math.max(1, SchedulerConfig.frozenReclassifyDivisor);
        double hardFreeze = SchedulerConfig.lodHardFreezeRadius;
        int budget = SchedulerConfig.aiTickBudget; // 0 = unlimited full ticks this server tick
        int spent = 0;
        // Graceful degradation: under server lag, double every LOD divisor (HIGH included) to shed AI load.
        double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        int stress = (SchedulerConfig.msptThrottle && mspt > SchedulerConfig.msptThrottleThreshold) ? 2 : 1;
        long round = frozenRound++;
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

            // Cheapest skip first: an already-FROZEN zombie has no target to track, so reclassify (and refresh
            // grid/sun-burn) only 1 of every frozenDiv activations. It stays put while skipped (no AI runs), so
            // the stale grid slot is fine; re-engages within frozenDiv activations once a target appears.
            // Stagger by the zombie's ACTIVATION index (round/buckets), not the raw tick round: a zombie only
            // reaches this line once every `buckets` ticks, so using raw round would step the residue by
            // buckets per activation and, when gcd(buckets,frozenDiv)>1, strand a fixed subset FROZEN forever.
            // round/buckets advances by exactly 1 per activation, cycling all residues regardless of buckets.
            if (frozenDiv > 1 && sz.lod() == LODLevel.FROZEN
                    && Math.floorMod(sz.id() + round / buckets, frozenDiv) != 0L) {
                continue;
            }

            // Player simulation-distance cutoff: if no player is within hardFreeze blocks, freeze WITHOUT the
            // target scan classify() does. NOTE this is deliberately PLAYER-only — a zombie hunting a non-player
            // target (villager/animal) with no player within hardFreeze is frozen too, i.e. autonomous hunts far
            // from any player pause until a player approaches. That tradeoff is why this defaults to 0 (off);
            // enable it only if you accept "nobody's watching → stop simulating" semantics.
            if (hardFreeze > 0.0) {
                Player np = level.getNearestPlayer(sz.entity(), hardFreeze);
                if (np == null) {
                    sz.pursuit().clearTarget();
                    sz.pursuit().clearMemory();
                    sz.setLod(LODLevel.FROZEN);
                    continue;
                }
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
            // Distance-tier throttle: distant zombies run their AI less often. Under server lag (stress=2)
            // every tier — HIGH included — is throttled extra to shed load.
            int divisor = 1;
            if (SchedulerConfig.throttleByLod) {
                divisor = switch (lod) {
                    case MEDIUM -> SchedulerConfig.lodMediumTickDivisor;
                    case LOW -> SchedulerConfig.lodLowTickDivisor;
                    default -> 1;
                };
            }
            divisor *= stress;
            if (!sz.dueThisActivation(divisor)) {
                continue;
            }
            // Hard per-tick budget: once this server tick has run aiTickBudget full ticks, the rest wait for
            // their next bucket activation. Blunt ceiling against population spikes (fairness is best-effort:
            // whoever this bucket iterates first — registry hash order, not id order — wins the budget; a bucket
            // permanently over budget starves its tail deterministically). LOD/grid/sun-burn already ran for all.
            if (budget > 0 && spent >= budget) {
                continue;
            }
            spent++;

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
