package com.dreykaoas.lethalbreed.tick;

import com.dreykaoas.lethalbreed.config.domain.engine.SchedulerConfig;
import com.dreykaoas.lethalbreed.ai.LodManager;
import com.dreykaoas.lethalbreed.dimension.WorldAiContext;
import com.dreykaoas.lethalbreed.entity.LodLevel;
import com.dreykaoas.lethalbreed.entity.move.WaterFear;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.probe.DevProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * What happens to ONE zombie on ONE activation, before the bucket pass decides whether to run its full
 * tick: the distance cutoff, the classify-grid-pack-burn-mood sequence, and the throttle divisor.
 *
 * <p>Separated from {@link LodBucketPass}, which owns the sweep, the buckets and the climber and swimmer
 * sets. This is the per-zombie half, and it is the half a performance question is usually about.
 */
final class ZombieActivation {
    private ZombieActivation() {}

    /** Record one profiling checkpoint and return the new "last timestamp", or {@code t} unchanged when
     *  profiling is off. No allocation, safe to call every activation of this hot per-zombie loop. */
    static long mark(int stage, boolean prof, long t) {
        if (!prof) {
            return t;
        }
        long n = System.nanoTime();
        DevProbe.sink.stage(stage, n - t);
        return n;
    }

    /** Player simulation-distance cutoff: if no player is within hardFreeze blocks, freeze WITHOUT the
     *  target scan classify() does, and report that the caller should skip this activation. NOTE this is
     *  deliberately PLAYER-only: a zombie hunting a non-player target (villager/animal) with no player
     *  within hardFreeze is frozen too, i.e. autonomous hunts far from any player pause until a player
     *  approaches. That tradeoff is why this defaults to 0 (off); enable it only if you accept "nobody's
     *  watching → stop simulating" semantics.
     *  A pack member is exempt: this cutoff wipes target AND memory before classify() runs, so with
     *  hardFreeze on, every migrating pack would stop dead the moment it left a player's radius,
     *  which is precisely when a migration is supposed to be happening. The cutoff still applies to
     *  every loose zombie, so its point (stop simulating what nobody watches) survives. */
    static boolean hardFreezeSkip(SmartZombie sz, ServerLevel level, double hardFreeze) {
        if (hardFreeze > 0.0 && !sz.pursuit().pack().inPack()) {
            Player np = level.getNearestPlayer(sz.entity(), hardFreeze);
            if (np == null) {
                sz.pursuit().clearTarget();
                sz.pursuit().clearMemory();
                // Same reason as LodManager's terminal branch: clearing only OUR target leaves vanilla's
                // never-stripped attack goal steering a zombie we have declared frozen.
                sz.entity().setTarget(null);
                sz.setLod(LodLevel.FROZEN);
                return true;
            }
        }
        return false;
    }

    /** Runs the classify → grid → pack → sun-burn → mood phase for one zombie activation and returns the
     *  LOD tier after mood processing (mood can un-freeze a zombie, so the tier must be re-read afterward). */
    static LodLevel classifyAndUpdate(SmartZombie sz, ServerLevel level, WorldAiContext classifyCtx,
                                        WorldAiContext ctx, boolean prof) {
        long t = prof ? System.nanoTime() : 0L;
        // Reclassify every activation so LOD + nearest-player (used for pillaring) stay fresh for
        // ALL buckets. A global tick%interval would only ever align with bucket 0.
        LodManager.classify(sz, level, classifyCtx.targetIndex());
        t = mark(DevProbe.CLASSIFY, prof, t);
        LodLevel lod = sz.lod();
        // Keep FROZEN zombies in the spatial grid (their tick(), which inserts them, is skipped below)
        // so neighbour queries still find them: a Screamer rallying idle zombies, a Healer healing them,
        // and sound propagation all target exactly these.
        ctx.spatialGrid().update(sz, sz.entity().blockPosition().getX(), sz.entity().blockPosition().getZ());
        t = mark(DevProbe.GRID, prof, t);
        // Pack decision runs here, BEFORE the FROZEN skip: a zombie with nothing to hunt is frozen, and
        // a frozen zombie looking for company is the nominal case for forming a pack, not an edge one.
        PackPass.decide(sz, ctx);
        t = mark(DevProbe.PACK, prof, t);
        // Re-assert the attribute ceilings. Spawn-time enforcement alone is not enough: vanilla stamps its
        // zombie-leader bonus AFTER finalizeSpawn, and stamps another one at runtime when a zombie summons
        // reinforcements: both multiply straight through a correction derived before they existed. Runs even
        // for FROZEN zombies (whose full tick() below is skipped), because a frozen zombie still bites.
        com.dreykaoas.lethalbreed.entity.genes.AttributeCaps.enforce(sz.entity());
        // Water rules alongside the sun for the same reason: a FROZEN zombie under water still drowns, and a
        // pathfinder that has not been told water is a wall would walk one in on its next hunt.
        WaterFear.applyNavigationRules(sz.entity());
        WaterFear.tickDrowning(level, sz, SchedulerConfig.tickBuckets);
        // Daylight burn must apply even to idle/FROZEN zombies (whose full tick() below is skipped).
        sz.applySunBurn(level);
        t = mark(DevProbe.SUNBURN, prof, t);
        // Mood (celebrate/flee/regen) also runs before the FROZEN skip so a targetless fleeing/celebrating
        // zombie still gets processed; it can un-freeze itself (LOD→HIGH), so re-read the tier afterward.
        sz.updateMood(level, ctx);
        mark(DevProbe.MOOD, prof, t);
        return sz.lod();
    }

    /** Distance-tier throttle divisor: distant zombies run their AI less often. Under server lag (stress=2)
     *  every tier (HIGH included) is throttled extra to shed load. */
    static int divisorFor(LodLevel lod, int stress) {
        int divisor = switch (lod) {
            case MEDIUM -> SchedulerConfig.lodMediumTickDivisor;
            case LOW -> SchedulerConfig.lodLowTickDivisor;
            default -> 1;
        };
        return divisor * stress;
    }

}
