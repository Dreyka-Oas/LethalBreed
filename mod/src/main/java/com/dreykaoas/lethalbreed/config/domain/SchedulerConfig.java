package com.dreykaoas.lethalbreed.config.domain;

/**
 * Tick scheduler, spatial hashing and LOD/throttle tuning. Distant zombies cost less; beyond the frozen
 * radius they stop ticking entirely.
 */
public final class SchedulerConfig {
    private SchedulerConfig() {}

    /** Number of stagger buckets for the tick scheduler (1000 zombies / N = updates per tick). */
    public static int tickBuckets = 5;

    /** Auto-pick {@link #tickBuckets} from population so each tick processes ~{@link #autoScaleBucketLoad}
     *  zombies, regardless of how many are alive. Overrides the fixed tickBuckets while on. */
    public static boolean autoScaleBuckets = false;
    /** Target zombies processed per tick when {@link #autoScaleBuckets} is on (buckets = ceil(pop / this)). */
    public static int autoScaleBucketLoad = 200;

    /** Hard cap on full AI {@code tick()} calls per server tick (0 = unlimited). Overflow waits for the
     *  zombie's next bucket activation. A blunt safety ceiling against population spikes. */
    public static int aiTickBudget = 0;

    /** Spatial hash cell size in blocks (XZ). */
    public static int spatialCellSize = 8;

    /** Max vertical distance (blocks) for spatial neighbour/sound queries. The grid is flat XZ, so without
     *  this a zombie 100 blocks above/below still counts as "near" (a surface Soigneur healing a zombie deep
     *  in a cave). 24 ≈ the widest query radius (Hurleur), so it never splits a legit pack but drops absurd
     *  cross-Y matches. 0 = ignore Y (legacy behaviour). */
    public static double spatialVerticalLimit = 24.0;

    /** LOD radii (blocks). Beyond {@link #lodLow} the mod freezes the zombie (stops ticking it). LOD is
     *  reclassified every bucket activation (a global tick-interval would only ever align with bucket 0). */
    public static double lodHigh = 40.0;
    public static double lodMedium = 64.0;
    public static double lodLow = 128.0;

    /** Hysteresis margin (blocks) added to a tier's outer edge before a zombie downgrades to a cheaper tier,
     *  so one hovering on a boundary doesn't flip tier (and re-path) every reclassify. 0 = off. */
    public static double lodHysteresis = 4.0;

    /** Player simulation-distance cutoff: beyond this radius from the nearest PLAYER a zombie is frozen
     *  WITHOUT even reclassifying (cheapest skip — no target scan). This also pauses autonomous hunts of
     *  non-player targets (villagers/animals) when no player is near, so it defaults to 0 (off). Set >=
     *  {@link #lodLow} if you accept "nobody's watching → stop simulating". 0 = never hard-freeze. */
    public static double lodHardFreezeRadius = 0.0;

    /** Already-FROZEN zombies reclassify (and refresh grid/sun-burn) only 1 of every N bucket activations,
     *  since they have no target to track. Higher = cheaper idle crowds, slower to re-engage. 1 = every time. */
    public static int frozenReclassifyDivisor = 4;

    // ---- Distance-tier throttle (borrowed from Immersive Optimization) ----
    /** Scale per-zombie AI frequency by LOD so distant zombies cost less. */
    public static boolean throttleByLod = true;
    /** MEDIUM-LOD zombies run their AI 1 activation out of N. */
    public static int lodMediumTickDivisor = 2;
    /** LOW-LOD zombies run their AI 1 activation out of N. */
    public static int lodLowTickDivisor = 4;
    /** Min ticks between forced re-paths (reduces pathfinder churn; nav also re-paths when done). */
    public static int navReissueInterval = 4;
    /** MEDIUM-LOD zombies re-path on {@code navReissueInterval * this} (distant = stale paths cost little). */
    public static int lodMediumNavMultiplier = 2;
    /** LOW-LOD zombies re-path on {@code navReissueInterval * this}. */
    public static int lodLowNavMultiplier = 4;

    /** When server MSPT exceeds {@link #msptThrottleThreshold}, temporarily double every LOD tick divisor
     *  (HIGH included) to shed AI load until the server recovers. Graceful degradation under stress. */
    public static boolean msptThrottle = false;
    /** Server mean-MSPT (ms) above which {@link #msptThrottle} kicks in (vanilla target tick = 50 ms). */
    public static double msptThrottleThreshold = 45.0;

    /** How often (ticks) to emit the dev perf recap (100 ticks = 5s). 0 disables (default — no log spam). */
    public static int debugLogInterval = 0;
}
