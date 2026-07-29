package com.dreykaoas.lethalbreed.effect.contamination;

import com.dreykaoas.lethalbreed.config.domain.ExpertConfig;

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
}
