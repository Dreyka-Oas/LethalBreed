package com.dreykaoas.lethalbreed.tick;

import com.dreykaoas.lethalbreed.config.domain.engine.SchedulerConfig;

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
    private final StageProfiler profiler;

    LodBucketPass(ZombieRegistry registry, DimensionManager dimensions, StageProfiler profiler) {
        this.registry = registry;
        this.dimensions = dimensions;
        this.profiler = profiler;
    }

    // Rotated each run() so the frozen-reclassify skip staggers WHICH frozen zombies refresh on a given
    // activation instead of always the same id-residue set.
    private long frozenRound = 0L;

    /** Record one profiling checkpoint and return the new "last timestamp", or {@code t} unchanged when
     *  profiling is off. No allocation — safe to call every activation of this hot per-zombie loop. */
    private static long mark(StageProfiler profiler, StageProfiler.Stage stage, boolean prof, long t) {
        if (!prof) {
            return t;
        }
        long n = System.nanoTime();
        profiler.add(stage, n - t);
        return n;
    }

    /** Player simulation-distance cutoff: if no player is within hardFreeze blocks, freeze WITHOUT the
     *  target scan classify() does, and report that the caller should skip this activation. NOTE this is
     *  deliberately PLAYER-only — a zombie hunting a non-player target (villager/animal) with no player
     *  within hardFreeze is frozen too, i.e. autonomous hunts far from any player pause until a player
     *  approaches. That tradeoff is why this defaults to 0 (off); enable it only if you accept "nobody's
     *  watching → stop simulating" semantics.
     *  A pack member is exempt: this cutoff wipes target AND memory before classify() runs, so with
     *  hardFreeze on, every migrating pack would stop dead the moment it left a player's radius —
     *  which is precisely when a migration is supposed to be happening. The cutoff still applies to
     *  every loose zombie, so its point (stop simulating what nobody watches) survives. */
    private static boolean hardFreezeSkip(SmartZombie sz, ServerLevel level, double hardFreeze) {
        if (hardFreeze > 0.0 && !sz.pursuit().pack().inPack()) {
            Player np = level.getNearestPlayer(sz.entity(), hardFreeze);
            if (np == null) {
                sz.pursuit().clearTarget();
                sz.pursuit().clearMemory();
                sz.setLod(LODLevel.FROZEN);
                return true;
            }
        }
        return false;
    }

    /** Runs the classify → grid → pack → sun-burn → mood phase for one zombie activation and returns the
     *  LOD tier after mood processing (mood can un-freeze a zombie, so the tier must be re-read afterward). */
    private LODLevel classifyAndUpdate(SmartZombie sz, ServerLevel level, WorldAIContext classifyCtx,
                                        WorldAIContext ctx, boolean prof) {
        long t = prof ? System.nanoTime() : 0L;
        // Reclassify every activation so LOD + nearest-player (used for pillaring) stay fresh for
        // ALL buckets — a global tick%interval would only ever align with bucket 0.
        LODManager.classify(sz, level, classifyCtx.targetIndex());
        t = mark(profiler, StageProfiler.Stage.CLASSIFY, prof, t);
        LODLevel lod = sz.lod();
        // Keep FROZEN zombies in the spatial grid (their tick() — which inserts them — is skipped below)
        // so neighbour queries still find them: a Hurleur rallying idle zombies, a Soigneur healing them,
        // and sound propagation all target exactly these.
        ctx.spatialGrid().update(sz, sz.entity().blockPosition().getX(), sz.entity().blockPosition().getZ());
        t = mark(profiler, StageProfiler.Stage.GRID, prof, t);
        // Pack decision runs here, BEFORE the FROZEN skip: a zombie with nothing to hunt is frozen, and
        // a frozen zombie looking for company is the nominal case for forming a pack, not an edge one.
        PackPass.decide(sz, ctx);
        t = mark(profiler, StageProfiler.Stage.PACK, prof, t);
        // Daylight burn must apply even to idle/FROZEN zombies (whose full tick() below is skipped).
        sz.applySunBurn(level);
        t = mark(profiler, StageProfiler.Stage.SUNBURN, prof, t);
        // Mood (celebrate/flee/regen) also runs before the FROZEN skip so a targetless fleeing/celebrating
        // zombie still gets processed; it can un-freeze itself (LOD→HIGH), so re-read the tier afterward.
        sz.updateMood(level, ctx);
        mark(profiler, StageProfiler.Stage.MOOD, prof, t);
        return sz.lod();
    }

    /** Distance-tier throttle divisor: distant zombies run their AI less often. Under server lag (stress=2)
     *  every tier — HIGH included — is throttled extra to shed load. */
    private static int divisorFor(LODLevel lod, int stress) {
        int divisor = switch (lod) {
            case MEDIUM -> SchedulerConfig.lodMediumTickDivisor;
            case LOW -> SchedulerConfig.lodLowTickDivisor;
            default -> 1;
        };
        return divisor * stress;
    }

    private void tickAndCollect(SmartZombie sz, ServerLevel level, WorldAIContext ctx, boolean prof,
                                 Set<SmartZombie> climbers, Set<SmartZombie> swimmers) {
        long tt = prof ? System.nanoTime() : 0L;
        sz.tick(level, ctx);
        mark(profiler, StageProfiler.Stage.TICK, prof, tt);
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

            if (hardFreezeSkip(sz, level, hardFreeze)) {
                continue;
            }

            boolean prof = StageProfiler.enabled();

            WorldAIContext classifyCtx = dimensions.get(sz.dimension());
            WorldAIContext ctx = dimensions.get(sz.dimension());
            LODLevel lod = classifyAndUpdate(sz, level, classifyCtx, ctx, prof);
            if (lod == LODLevel.FROZEN) {
                continue;
            }
            // Distance-tier throttle: distant zombies run their AI less often. Under server lag (stress=2)
            // every tier — HIGH included — is throttled extra to shed load.
            int divisor = SchedulerConfig.throttleByLod ? divisorFor(lod, stress) : stress;
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

            tickAndCollect(sz, level, ctx, prof, climbers, swimmers);
        }
    }

    private void untrack(SmartZombie sz) {
        WorldAIContext ctx = dimensions.get(sz.dimension());
        ctx.spatialGrid().remove(sz);
        registry.remove(sz.id());
    }
}
