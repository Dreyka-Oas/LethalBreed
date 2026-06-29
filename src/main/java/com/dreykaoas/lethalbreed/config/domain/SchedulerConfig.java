package com.dreykaoas.lethalbreed.config.domain;

/**
 * Tick scheduler, spatial hashing and LOD/throttle tuning. Distant zombies cost less; beyond the frozen
 * radius they stop ticking entirely.
 */
public final class SchedulerConfig {
    private SchedulerConfig() {}

    /** Number of stagger buckets for the tick scheduler (1000 zombies / N = updates per tick). */
    public static int tickBuckets = 5;

    /** Spatial hash cell size in blocks (XZ). */
    public static int spatialCellSize = 8;

    /** LOD radii (blocks). Beyond {@link #lodLow} the mod freezes the zombie (stops ticking it). LOD is
     *  reclassified every bucket activation (a global tick-interval would only ever align with bucket 0). */
    public static double lodHigh = 40.0;
    public static double lodMedium = 64.0;
    public static double lodLow = 128.0;

    // ---- Distance-tier throttle (borrowed from Immersive Optimization) ----
    /** Scale per-zombie AI frequency by LOD so distant zombies cost less. */
    public static boolean throttleByLod = true;
    /** MEDIUM-LOD zombies run their AI 1 activation out of N. */
    public static int lodMediumTickDivisor = 2;
    /** LOW-LOD zombies run their AI 1 activation out of N. */
    public static int lodLowTickDivisor = 4;
    /** Min ticks between forced re-paths (reduces pathfinder churn; nav also re-paths when done). */
    public static int navReissueInterval = 4;

    /** How often (ticks) to emit the dev perf recap (100 ticks = 5s). 0 disables (default — no log spam). */
    public static int debugLogInterval = 0;
}
