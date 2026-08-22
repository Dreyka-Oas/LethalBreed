package com.dreykaoas.lethalbreed.tick;

import com.dreykaoas.lethalbreed.config.domain.engine.SchedulerConfig;

import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.dimension.WorldAiContext;
import com.dreykaoas.lethalbreed.entity.LodLevel;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieRegistry;
import com.dreykaoas.lethalbreed.probe.DevProbe;
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

    // Rotated each run() so the frozen-reclassify skip staggers WHICH frozen zombies refresh on a given
    // activation instead of always the same id-residue set.
    private long frozenRound = 0L;

    private void tickAndCollect(SmartZombie sz, ServerLevel level, WorldAiContext ctx, boolean prof,
                                 Set<SmartZombie> climbers, Set<SmartZombie> swimmers) {
        long tt = prof ? System.nanoTime() : 0L;
        sz.tick(level, ctx);
        ZombieActivation.mark(DevProbe.TICK, prof, tt);
        if (sz.isClimbing()) {
            climbers.add(sz);
        }
        if (sz.isSwimming()) {
            swimmers.add(sz);
        }
    }

    void run(MinecraftServer server, int buckets, int currentBucket, Set<SmartZombie> climbers, Set<SmartZombie> swimmers) {
        // buckets is supplied by the scheduler (the same value it used to derive currentBucket), so membership
        // stays consistent even when autoScaleBuckets recomputes it from population each tick. Computing the
        // bucket live (id % buckets) means a count change re-spreads every zombie at once, none stranded.
        int frozenDiv = Math.max(1, SchedulerConfig.frozenReclassifyDivisor);
        double hardFreeze = SchedulerConfig.lodHardFreezeRadius;
        int budget = SchedulerConfig.aiTickBudget; // 0 = unlimited full ticks this server tick
        int spent = 0;
        // Graceful degradation: under server lag, double every LOD divisor (HIGH included) to shed AI load.
        double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        int stress = (SchedulerConfig.msptThrottle && mspt > SchedulerConfig.msptThrottleThreshold) ? 2 : 1;
        long round = frozenRound++;
        // Per-stage timing: one volatile read per TICK when disabled (hoisted out of the loop: it used to be
        // re-evaluated per zombie). DevProbe.sink is volatile, so this read is not constant-folded even on a
        // shipped jar with no sink installed. The real cost is one volatile load here, once per bucket run.
        boolean prof = DevProbe.on();
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
            if (frozenDiv > 1 && sz.lod() == LodLevel.FROZEN
                    && Math.floorMod(sz.id() + round / buckets, frozenDiv) != 0L) {
                continue;
            }

            if (ZombieActivation.hardFreezeSkip(sz, level, hardFreeze)) {
                continue;
            }

            WorldAiContext classifyCtx = dimensions.get(sz.dimension());
            WorldAiContext ctx = dimensions.get(sz.dimension());
            LodLevel lod = ZombieActivation.classifyAndUpdate(sz, level, classifyCtx, ctx, prof);
            if (lod == LodLevel.FROZEN) {
                continue;
            }
            // Distance-tier throttle: see ZombieActivation.divisorFor.
            int divisor = SchedulerConfig.throttleByLod ? ZombieActivation.divisorFor(lod, stress) : stress;
            if (!sz.dueThisActivation(divisor)) {
                continue;
            }
            // Hard per-tick budget: once this server tick has run aiTickBudget full ticks, the rest wait for
            // their next bucket activation. Blunt ceiling against population spikes (fairness is best-effort:
            // whoever this bucket iterates first (registry hash order, not id order) wins the budget; a bucket
            // permanently over budget starves its tail deterministically). LOD/grid/sun-burn already ran for all.
            if (budget > 0 && spent >= budget) {
                continue;
            }
            spent++;

            tickAndCollect(sz, level, ctx, prof, climbers, swimmers);
        }
    }

    private void untrack(SmartZombie sz) {
        WorldAiContext ctx = dimensions.get(sz.dimension());
        ctx.spatialGrid().remove(sz);
        registry.remove(sz.id());
    }
}
