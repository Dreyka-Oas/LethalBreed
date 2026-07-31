package com.dreykaoas.lethalbreed.effect.contamination;

import com.dreykaoas.lethalbreed.config.domain.ExpertConfig;

import java.util.Random;

/**
 * The plague's random-draw rules as pure functions. **No Minecraft imports, and none may be added** —
 * that is the whole point of this class: {@link ContaminationState} registers Fabric attachment types
 * in its static initialiser and so cannot be loaded by a headless unit test, which left every draw
 * rule the plague uses permanently untested. Findings #5 and #12 are both instances of the same
 * failure mode that produced: one rule, written in several places, quietly drifting apart.
 *
 * <p>Add a draw rule HERE and call it from the timer classes — never inline a fresh copy at a call site.
 */
public final class ContaminationRoll {
    private ContaminationRoll() {}

    /**
     * Gap-shortening factor for a flare timer: higher intensity → shorter gap, so we hand back
     * {@code 1/mult}. The divisor is floored by {@code expertContamIntensityFloor} both to avoid a
     * divide-by-zero and to cap how short an operator can drive the gaps.
     */
    public static double intensityFactor(double mult) {
        return 1.0 / Math.max(ExpertConfig.expertContamIntensityFloor, mult);
    }

    /**
     * The one uniform draw in {@code [min, max]} behind every plague timer and every plague magnitude.
     * Floors BOTH ends at 0 independently, then REORDERS an inverted pair before lerping —
     * {@code ConfigBoundsTable} bounds each option independently and never the relation between two, so an
     * operator can put min above max with both values perfectly in range. Without the reorder that yields a
     * draw below the minimum, or negative: a healing plague, or a cure threshold that never fires (audit #12).
     *
     * <p>Because {@code max} is floored on its own, a pair with BOTH ends negative does not merely clip —
     * it collapses to the constant 0 (a zero-width range), so the caller's draw is 0 rather than negative.
     * That is deliberate: 0 damage / 0 percent / 0 ticks is the safe reading of a nonsensical range.
     */
    public static double uniform(Random rng, double min, double max) {
        min = Math.max(0.0, min);
        max = Math.max(0.0, max);
        if (min > max) {
            double tmp = min;
            min = max;
            max = tmp;
        }
        return min + rng.nextDouble() * (max - min);
    }

    /**
     * Draw a percentage threshold in {@code [minPct, maxPct]}, then roll against it. True means the
     * event fires. Consumes exactly two values from {@code rng}, in that order.
     */
    public static boolean percent(Random rng, double minPct, double maxPct) {
        double pct = uniform(rng, minPct, maxPct);
        return rng.nextDouble() * 100.0 < pct;
    }
}
