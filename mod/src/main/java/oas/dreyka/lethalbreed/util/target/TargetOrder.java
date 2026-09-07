package oas.dreyka.lethalbreed.util.target;

import oas.dreyka.lethalbreed.config.domain.CombatMoveConfig;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * Orders the candidate list {@link TargetSelector} hands it: nearest first, ties broken by height, and
 * exact ties broken at random.
 *
 * <p>Split out because this is the hot loop, not the policy. It runs for every zombie on every bucket
 * activation and was measured at roughly 40% of the mod's tick time, so every allocation and every
 * repeated distance computation here matters.
 */
final class TargetOrder {
    private TargetOrder() {}

    /** 4 = (2 blocks)²: distances differing by less than 2 blocks count as equally close. */
    private static final double TIE_BAND = 4.0;

    /**
     * Added to the band of a candidate standing in water, which pushes it behind every candidate on dry
     * land whatever the two distances are.
     *
     * <p>A zombie that cannot swim drowns in the water it walks into, so the nearest prey is the wrong pick
     * when that prey is wading and another one is not: the zombie commits to a target it can only reach by
     * killing itself. Deprioritising rather than rejecting keeps a horde able to finish off someone who
     * fled into a lake once nothing dry is left.
     *
     * <p>2^32 cannot collide with a real band: the largest one is {@code targetDetectRadius² / 4}, and
     * {@code TargetingBounds} clamps that radius at 128, so no band can reach 4097.
     */
    private static final long WET_PENALTY = 1L << 32;

    /**
     * Sorts {@code candidates} in place and returns the squared distance of each, in the same order.
     *
     * <p>Dry prey first, then nearest, then ties broken by height: among two roughly-as-close candidates
     * (one overhead, one at our level) the one nearest in HEIGHT wins, because a target at the zombie's own
     * level is reachable without a climb.
     */
    static double[] shuffleAndOrder(List<LivingEntity> candidates, Mob self) {
        int n = candidates.size();
        // Shuffle first so entities that end up EXACTLY tied (same distance band AND same height gap)
        // resolve at random: the sort below is stable, so it preserves this order for equal keys.
        for (int i = n - 1; i > 0; i--) {
            int j = self.getRandom().nextInt(i + 1);
            LivingEntity tmp = candidates.get(i);
            candidates.set(i, candidates.get(j));
            candidates.set(j, tmp);
        }
        // Keys are computed ONCE per candidate rather than inside a comparator: a comparator calling
        // distanceToSqr twice per comparison performs ~2*n*log(n) distance computations for n elements.
        final double selfY = self.getY();
        // Read once: the option cannot change mid-sort, and a zombie allowed to swim has no reason to rank
        // a wading target behind a dry one.
        final boolean avoidWater = CombatMoveConfig.cannotSwim;
        long[] band = new long[n];
        double[] heightGap = new double[n];
        double[] distSq = new double[n];
        for (int i = 0; i < n; i++) {
            LivingEntity e = candidates.get(i);
            double d = self.distanceToSqr(e);
            distSq[i] = d;
            band[i] = band(d, avoidWater && e.isInWater());
            heightGap[i] = Math.abs(e.getY() - selfY);
        }
        insertionSort(candidates, band, heightGap, distSq, n);
        return distSq;
    }

    /** The primary sort key: distance band, pushed past every dry candidate when {@code inWater}. Package
     *  private so the ordering can be checked without a world to stand entities in. */
    static long band(double distSq, boolean inWater) {
        long b = (long) (distSq / TIE_BAND);
        return inWater ? b + WET_PENALTY : b;
    }

    /**
     * Insertion sort over the parallel arrays: n is small (zombies are excluded upstream, so these are just
     * the nearby prey), it is stable so exact ties keep the shuffle's random order, and unlike sorting an
     * {@code Integer[]} index array it boxes nothing on a path that runs per zombie per activation.
     */
    private static void insertionSort(List<LivingEntity> candidates, long[] band, double[] heightGap,
                                      double[] distSq, int n) {
        for (int i = 1; i < n; i++) {
            LivingEntity ce = candidates.get(i);
            long cb = band[i];
            double ch = heightGap[i];
            double cd = distSq[i];
            int j = i - 1;
            while (j >= 0 && (band[j] > cb || (band[j] == cb && heightGap[j] > ch))) {
                candidates.set(j + 1, candidates.get(j));
                band[j + 1] = band[j];
                heightGap[j + 1] = heightGap[j];
                distSq[j + 1] = distSq[j];
                j--;
            }
            candidates.set(j + 1, ce);
            band[j + 1] = cb;
            heightGap[j + 1] = ch;
            distSq[j + 1] = cd;
        }
    }
}
