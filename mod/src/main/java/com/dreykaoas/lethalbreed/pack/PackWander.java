package com.dreykaoas.lethalbreed.pack;

import com.dreykaoas.lethalbreed.config.domain.PackConfig;

import java.util.Random;

/**
 * Where a pack goes next.
 *
 * <p><b>Nothing here may ever consult a player.</b> That is the whole point of the mechanic: a pack drifts
 * because of its own momentum, not because someone is standing somewhere. The rule takes only its current
 * position, its current heading, the world border and an injected {@link Random} — there is no parameter
 * through which a player position could arrive, which is a stronger guarantee than a comment.
 * {@code PackNoPlayerAccessTest} enforces the same thing across the package.
 *
 * <p>Vanilla points of interest were considered and rejected: {@code PoiManager} forces its sections to load
 * from disk, and a migrating pack is by definition heading for chunks that are not resident — the cost lands
 * on the server thread, for terrain that may not even be generated yet.
 *
 * <p>The heading is <b>persistent and only slightly perturbed per leg</b>. That is what separates a
 * migration from a random walk: with an unbounded turn the pack jitters around its starting point forever
 * and never actually goes anywhere.
 */
public final class PackWander {
    private PackWander() {}

    public record Destination(int x, int z) {}

    /**
     * Pick the next destination and the heading to carry into the leg after it.
     *
     * @param outHeading a two-slot scratch array, written with the new unit heading. Passed in rather than
     *                   allocated so a round-robin over hundreds of packs stays allocation-free.
     */
    public static Destination next(int fromX, int fromZ, double headingX, double headingZ,
                                   int borderMinX, int borderMinZ, int borderMaxX, int borderMaxZ,
                                   Random rng, double[] outHeading) {
        double angle = baseAngle(headingX, headingZ)
                + Math.toRadians(PackConfig.packTurnDegrees) * (rng.nextDouble() * 2.0 - 1.0);
        double hx = Math.cos(angle);
        double hz = Math.sin(angle);

        // Both bounds are independent operator-facing options, so nothing stops them being inverted; a
        // nextInt(bound <= 0) would take down the server thread.
        int lo = Math.min(PackConfig.packLegMin, PackConfig.packLegMax);
        int hi = Math.max(PackConfig.packLegMin, PackConfig.packLegMax);
        int leg = lo + rng.nextInt(hi - lo + 1);

        int wantX = fromX + (int) Math.round(hx * leg);
        int wantZ = fromZ + (int) Math.round(hz * leg);
        int gotX = Math.clamp(wantX, borderMinX, borderMaxX);
        int gotZ = Math.clamp(wantZ, borderMinZ, borderMaxZ);

        // Bounce whichever axis hit the border. Without this the pack would keep aiming straight into the
        // wall every leg, clamp to the same spot, and sit there for the rest of the world's life.
        outHeading[0] = gotX == wantX ? hx : -hx;
        outHeading[1] = gotZ == wantZ ? hz : -hz;
        return new Destination(gotX, gotZ);
    }

    /** A fresh pack has no heading yet; atan2(0,0) is 0, which points due east — a fine arbitrary start,
     *  but say so rather than leave the reader wondering whether it is a bug. */
    private static double baseAngle(double headingX, double headingZ) {
        return Math.atan2(headingZ, headingX);
    }
}
