package com.dreykaoas.lethalbreed.config.domain;

/**
 * EXPERT / low-level constants — the numeric tolerances, safety clamps and vanilla-derived magic numbers that
 * are NOT gameplay knobs. They live here (and in their own "Expert" GUI tab) so nothing in the mod is truly
 * hard-coded, while keeping them out of the normal gameplay tabs. Changing these can BREAK movement, spawning
 * or plague correctness — the defaults are the values the code was written against. Touch only if you know
 * exactly what each one guards.
 */
public final class ExpertConfig {
    private ExpertConfig() {}

    // ---- Movement math tolerances ----
    /** Dead-zone (blocks) below which a signed delta quantises to no cardinal step (MoveMath.stepSign). */
    public static double expertStepDeadzone = 0.5;
    /** Epsilon subtracted before ceil() when computing a zombie's break height from its hitbox. */
    public static double expertBreakHeightEpsilon = 1.0e-4;
    /** Squared-length gate below which a heading is treated as degenerate (no facing update). */
    public static double expertHeadingEpsilon = 1.0e-4;

    // ---- Pillar-climb internals ----
    /** Div-by-zero guard on the horizontal heading length during a pillar finish hop. */
    public static double expertPillarHeadingEpsilon = 0.001;
    /** Vertical offset (blocks) above the head at which the pillar climb probes for a ceiling. */
    public static double expertPillarCeilingOffset = 0.25;
    /** Height gained (blocks) before a pillar climber drops a support block into the vacated cell. */
    public static double expertPillarSupportHeight = 1.0;

    // ---- Variation safety floor ----
    /** Hard floor for the SCALE / MOVEMENT_SPEED variation factor, so an extreme low roll can't make a zombie
     *  invisibly tiny or frozen in place. */
    public static double expertAttributeFloor = 0.05;

    // ---- Vanilla spawn constant ----
    /** Vanilla mob-cap divisor (17² = the 17×17-chunk spawn area). Lower = denser natural spawns. Must stay ≥ 1
     *  (a 0 would divide by zero). */
    public static int expertMobcapChunkDivisor = 289;

    // ---- Contamination safety clamps ----
    /** Lower clamp on the plague intensity multiplier before it is used as a reciprocal (gap scaling). */
    public static double expertContamIntensityFloor = 1.0e-3;
    /** Lower clamp on the dev plague time-compression factor, to avoid a divide-by-zero in the timers. */
    public static double expertContamTimeScaleFloor = 1.0e-3;
}
