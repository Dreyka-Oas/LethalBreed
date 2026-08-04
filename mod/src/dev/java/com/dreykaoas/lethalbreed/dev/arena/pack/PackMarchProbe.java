package com.dreykaoas.lethalbreed.dev.arena.pack;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.pack.PackState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.List;

/**
 * The march stage's observations, kept out of {@link PackHarness} because it is the only stage carrying
 * state of its own: a start distance, a running best, a spread, and whether a clean sample was ever taken.
 *
 * <p>The destination is forced every tick rather than left to the wander rule. A random walk would take the
 * pack somewhere unpredictable, and the check would then measure the world's luck instead of the code.
 */
final class PackMarchProbe {

    /** How far the pack must close, and how far a member may drift, for the march to count. */
    static final double MIN_CLOSED = 20.0;
    static final double MAX_SPREAD = 40.0;

    private double startDist = -1.0;
    private double bestDist = Double.MAX_VALUE;
    private double maxSpread;
    private boolean sampled;

    /** Push the pack east down the corridor and record how it travels. */
    void observe(ServerLevel ow, List<Zombie> members, int expected) {
        PackState pack = firstPack(ow);
        if (pack == null) {
            return;
        }
        pack.destX = PackArena.CX + 120;
        pack.destZ = PackArena.CZ;
        pack.dwellUntil = 0L;

        double d = Math.hypot(pack.destX - pack.x, pack.destZ - pack.z);
        if (startDist < 0.0) {
            startDist = d;
        }
        int live = 0;
        double spread = 0.0;
        for (Zombie z : members) {
            SmartZombie sz = GameState.REGISTRY.get(z.getId());
            if (sz == null || !sz.isValid()) {
                continue;
            }
            live++;
            spread = Math.max(spread, Math.hypot(sz.x() - pack.x, sz.z() - pack.z));
        }
        // Only a sample where every member the mod ACTUALLY TRACKS is still alive may set the record. A
        // depopulated arena otherwise scores a tiny spread and a closing distance, and reads better than one
        // that works. The bar is the count registered at build, not the count requested: this world
        // intermittently drops one or two of a spawn row before they ever reach the registry, and a zombie
        // that never entered the experiment must not be able to invalidate it — whereas a tracked member
        // that dies mid-march still does, which is the property this guard exists for.
        if (expected > 0 && live == expected) {
            sampled = true;
            bestDist = Math.min(bestDist, d);
            maxSpread = Math.max(maxSpread, spread);
        }
    }

    private static PackState firstPack(ServerLevel ow) {
        for (PackState p : GameState.DIMENSIONS.get(ow.dimension()).packManager().all()) {
            return p;
        }
        return null;
    }

    boolean sampled() {
        return sampled;
    }

    double startDist() {
        return startDist;
    }

    /** Blocks the centroid closed on the destination. Zero when nothing was ever measured — the caller
     *  reports that case separately rather than letting an unmeasured march read as a stationary one. */
    double closed() {
        return sampled ? startDist - bestDist : 0.0;
    }

    double maxSpread() {
        return maxSpread;
    }
}
