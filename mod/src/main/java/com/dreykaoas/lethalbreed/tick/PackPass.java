package com.dreykaoas.lethalbreed.tick;

import com.dreykaoas.lethalbreed.config.domain.PackConfig;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.pack.PackJoinRule;
import com.dreykaoas.lethalbreed.pack.PackManager;
import com.dreykaoas.lethalbreed.pack.PackState;
import com.dreykaoas.lethalbreed.pack.PackTether;

import java.util.ArrayList;
import java.util.List;

/**
 * One zombie's pack decision, taken from inside the bucket pass.
 *
 * <p>Contains no rule of its own: it collects the neighbourhood into primitives, hands them to
 * {@link PackJoinRule}, and applies whatever comes back. The rule is unit-tested; this is the plumbing that
 * feeds it.
 *
 * <p><b>Cost.</b> A zombie decides one activation in {@code packDecisionDivisor} (8 by default), and a
 * decision is a single {@code queryRadiusInto} on a shared scratch list — no allocation, and bounded by
 * {@code packScanCap} neighbours. At 300 zombies over 5 buckets that is roughly seven decisions a tick.
 *
 * <p>Called <b>before</b> the FROZEN skip in {@code LodBucketPass} on purpose: a zombie with nothing to hunt
 * is frozen, and a frozen zombie looking for company is the nominal case, not an edge one.
 */
public final class PackPass {
    private PackPass() {}

    /** Scratch reused across every zombie and every tick. Server thread only, like everything around it. */
    private static final List<SmartZombie> NEIGHBOURS = new ArrayList<>(32);
    private static long[] packIds = new long[32];
    private static int[] entityIds = new int[32];
    private static double[] distSq = new double[32];

    public static void decide(SmartZombie sz, WorldAIContext ctx) {
        if (!PackConfig.packEnabled) {
            return;
        }
        PackManager manager = ctx.packManager();
        PackTether tether = sz.pursuit().pack();
        if (!tether.dueToDecide(Math.max(1, PackConfig.packDecisionDivisor))) {
            return;
        }
        PackState mine = tether.inPack() ? manager.get(tether.packId()) : null;
        if (tether.inPack() && mine == null) {
            // Its pack was dissolved or merged away while it was not looking. Cut it loose rather than let
            // it keep a dangling id that no lookup will ever resolve.
            manager.leave(sz);
            return;
        }

        int n = collectNeighbours(sz, ctx);
        double distToCentroidSq = mine == null ? 0.0 : centroidDistSq(sz, mine);
        PackJoinRule.Decision d = PackJoinRule.decide(
                tether.packId(), sz.id(), mine == null ? 0 : mine.totalMembers(),
                distToCentroidSq, tether.strayCount(), packIds, entityIds, distSq, n);

        if (mine != null) {
            tether.setStrayCount(PackJoinRule.nextStrayCount(distToCentroidSq, tether.strayCount()));
        }
        switch (d.kind()) {
            case FORM -> manager.form(sz);
            case JOIN -> {
                PackState target = manager.get(d.packId());
                if (target != null) {
                    manager.join(sz, target);
                }
            }
            case LEAVE -> manager.leave(sz);
            case NONE -> { }
        }
    }

    private static double centroidDistSq(SmartZombie sz, PackState pack) {
        double dx = sz.x() - pack.x;
        double dz = sz.z() - pack.z;
        return dx * dx + dz * dz;
    }

    /** Fill the parallel arrays with up to {@code packScanCap} neighbours; returns how many. */
    private static int collectNeighbours(SmartZombie sz, WorldAIContext ctx) {
        NEIGHBOURS.clear();
        ctx.spatialGrid().queryRadiusInto(NEIGHBOURS, sz.x(), sz.y(), sz.z(), PackConfig.packCohesionRadius);
        int cap = Math.max(1, PackConfig.packScanCap);
        grow(cap);
        int n = 0;
        for (SmartZombie other : NEIGHBOURS) {
            if (n >= cap) {
                break;
            }
            if (other == sz || !other.isValid()) {
                continue;
            }
            double dx = other.x() - sz.x();
            double dz = other.z() - sz.z();
            packIds[n] = other.pursuit().pack().packId();
            entityIds[n] = other.id();
            distSq[n] = dx * dx + dz * dz;
            n++;
        }
        return n;
    }

    private static void grow(int cap) {
        if (packIds.length >= cap) {
            return;
        }
        packIds = new long[cap];
        entityIds = new int[cap];
        distSq = new double[cap];
    }
}
