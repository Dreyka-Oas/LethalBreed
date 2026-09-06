package oas.dreyka.lethalbreed.tick;

import oas.dreyka.lethalbreed.config.domain.PackConfig;
import oas.dreyka.lethalbreed.dimension.WorldAiContext;
import oas.dreyka.lethalbreed.entity.SmartZombie;
import oas.dreyka.lethalbreed.pack.rule.PackJoinRule;
import oas.dreyka.lethalbreed.pack.PackManager;
import oas.dreyka.lethalbreed.pack.PackState;
import oas.dreyka.lethalbreed.pack.rule.PackTether;
import oas.dreyka.lethalbreed.probe.DevProbe;

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
 * decision is a single {@code queryRadiusInto} on a shared scratch list, no allocation, and bounded by
 * {@code packScanCap} neighbours. At 300 zombies over 5 buckets that is roughly seven decisions a tick.
 *
 * <p>Called <b>before</b> the FROZEN skip in {@code LodBucketPass} on purpose: a zombie with nothing to hunt
 * is frozen, and a frozen zombie looking for company is the nominal case, not an edge one.
 */
public final class PackPass {
    private PackPass() {}

    /** Scratch reused across every zombie and every tick. Server thread only, like everything around it.
     *  Emptied on the way out of {@link #collectNeighbours}, never merely on the way in: a list cleared only
     *  before its next fill still holds the previous batch for however long that next fill takes to arrive,
     *  which after the last pack decision of a world is forever. Up to packScanCap zombies, each pinning its
     *  ServerLevel and from there the whole server graph (same shape as audit #8). */
    private static final List<SmartZombie> NEIGHBOURS = new ArrayList<>(32);
    private static long[] packIds = new long[32];
    private static int[] entityIds = new int[32];
    private static double[] distSq = new double[32];

    public static void decide(SmartZombie sz, WorldAiContext ctx) {
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
            // Its pack was dissolved or merged away while it was not looking. Cut it loose, so it stops
            // carrying a dangling id that no lookup will ever resolve.
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
        if (DevProbe.tracing(DevProbe.PACKS)) {
            // The three numbers that separate the ways "no pack formed" can happen: the rule was never
            // offered a neighbour (n), it was offered some and declined (kind=NONE), or it acted. Without
            // them the verdict says only that nothing happened, the least useful thing to know.
            DevProbe.sink.trace(DevProbe.PACKS, "id=" + sz.id()
                    + " at (" + Math.round(sz.x()) + ", " + Math.round(sz.z()) + ")"
                    + " n=" + n + " pack=" + tether.packId()
                    + " -> " + d.kind());
        }
        switch (d.kind()) {
            case FORM -> manager.form(sz);
            case JOIN -> {
                PackState target = manager.get(d.packId());
                // The rule can only count the members it was shown, and packScanCap (16) sits below
                // packMaxSize (24), so its own fullness test can never trip at the shipped defaults. The
                // roster is the only place the real total exists, and it is right here.
                if (target != null && target.totalMembers() < PackConfig.packMaxSize) {
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
    private static int collectNeighbours(SmartZombie sz, WorldAiContext ctx) {
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
        NEIGHBOURS.clear();
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
