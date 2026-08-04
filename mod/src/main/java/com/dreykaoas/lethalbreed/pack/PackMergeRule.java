package com.dreykaoas.lethalbreed.pack;

import com.dreykaoas.lethalbreed.config.domain.PackConfig;

/**
 * Whether two packs become one, and when a pack stops existing.
 *
 * <p>Pure like {@link PackJoinRule}: two centroids, two headings, two sizes, and no world.
 *
 * <p><b>The heading test is not decoration.</b> Merging on proximity alone makes "one big pack" an
 * absorbing state — packs only ever fuse, never split, so given enough time every zombie in the dimension
 * ends up in a single mass and the whole feature reads as one blob wandering the map. Requiring the two
 * headings to broadly agree means packs crossing in opposite directions pass through each other, which is
 * both what a migration looks like and what keeps the population distributed.
 */
public final class PackMergeRule {
    private PackMergeRule() {}

    /**
     * True when the two packs are close enough and travelling in broadly the same direction.
     *
     * <p>Headings are expected to be unit vectors, so their dot product is the cosine of the angle between
     * them: 1 is the same course, 0 is a right angle, -1 is head-on.
     */
    public static boolean shouldMerge(double ax, double az, double ahx, double ahz, int aSize,
                                      double bx, double bz, double bhx, double bhz, int bSize) {
        double dx = bx - ax;
        double dz = bz - az;
        double r = PackConfig.packMergeRadius;
        if (dx * dx + dz * dz > r * r) {
            return false;
        }
        return ahx * bhx + ahz * bhz >= PackConfig.packMergeHeadingDot;
    }

    /**
     * Which of the two pack ids survives the merge: the bigger pack, or the smaller id at equal size.
     *
     * <p>The tiebreak exists so the outcome does not depend on which of the two the round-robin happened to
     * visit first — the same pair must always collapse the same way, or the surviving id would flip between
     * runs and the saved data would churn for nothing.
     */
    public static long survivor(long aId, int aSize, long bId, int bSize) {
        if (aSize != bSize) {
            return aSize > bSize ? aId : bId;
        }
        return Math.min(aId, bId);
    }

    /** True when a pack has shrunk below the floor for longer than the grace period. An empty pack goes
     *  at once: keeping it alive would only advance and re-materialise nothing. */
    public static boolean shouldDissolve(int totalMembers, long ticksBelowMin) {
        if (totalMembers <= 0) {
            return true;
        }
        return totalMembers < PackConfig.packMinSize
                && ticksBelowMin >= PackConfig.packDissolveGraceTicks;
    }
}
