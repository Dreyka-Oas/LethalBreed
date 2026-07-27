package com.dreykaoas.lethalbreed.config.domain;

/**
 * Target acquisition (any living entity, not just players), coexistence with optimization mods, and sound
 * perception used to locate prey behind walls.
 */
public final class TargetingConfig {
    private TargetingConfig() {}

    // ---- Targeting (any living entity, not just players) ----
    /** Detection radius (blocks) for acquiring a target entity. Larger = sees prey farther, costs more. */
    public static double targetDetectRadius = 40.0;
    /**
     * Vertical half-height (blocks) of the broad-phase box the target scan sweeps. The scan is the single
     * most expensive thing the mod does per zombie activation — measured at ~52% of the reclassify stage,
     * itself ~40% of total AI time — and its cost scales with the VOLUME swept, not with how many entities
     * are found. At {@link #targetDetectRadius} = 40 the default box is 80 blocks tall, so most of what it
     * sweeps is sky and bedrock.
     *
     * <p>Lowering this shrinks the swept volume proportionally. It is a REAL behaviour change, which is why
     * it defaults to off: a target within the detect radius but higher/lower than this is no longer acquired
     * by sight (a player on a 30-block tower stops being seen from the ground). Sound-based memory is
     * unaffected. 24 mirrors {@link SchedulerConfig#spatialVerticalLimit}, the same tradeoff the mod already
     * makes for neighbour and sound queries, and cuts the swept volume ~1.7x.
     *
     * <p>0 = off (sweep the full spherical radius vertically — the original behaviour).
     */
    public static double targetDetectVerticalRadius = 0.0;
    /** Require line of sight to acquire a target by VISION — opaque blocks block sight, translucent ones
     *  (glass, ice, leaves) don't. A target behind a solid wall is found via sound instead, not sight. */
    public static boolean requireLineOfSight = true;
    /** Make zombies actually attack their acquired target (deal damage), not just approach it. */
    public static boolean attackAllTargets = true;
    /** Strip vanilla target-selection goals so our "nearest living entity" pick is authoritative — the
     *  zombie always retargets to the closest entity instead of vanilla re-locking onto the player. */
    public static boolean forceNearestTarget = true;
    /** Restrict targeting to PLAYERS only — when on, non-player living entities (villagers, animals, other
     *  mobs) are never acquired as targets. Default off = hunt any valid living entity. */
    public static boolean targetPlayersOnly = false;
    /** Short-term memory: once a target is lost (out of sight AND out of hearing), keep heading to its LAST
     *  known position for this many ticks before giving up (200 = 10s). Any live detection (a nearer/visible
     *  or heard entity) overrides the memory immediately — the nearest DETECTED target always wins. 0 = off. */
    public static int targetMemoryTicks = 200;
    /** Target stickiness: once committed to a target, don't switch to a newly-visible other unless the new one
     *  is CLOSER than (current distance ÷ this factor). >1 = sticky (1.5 = only switch when the other is ~33%
     *  nearer). Stops a zombie mid-dig from thrashing between two equally-far targets — while breaking a wall
     *  toward its prey, LOS to that prey is blocked BY the wall, so without this it would keep flipping to
     *  whatever else is momentarily visible and never finish the block. 1 = off (always nearest visible). */
    public static double targetSwitchMargin = 1.5;

    // ---- Coexistence with optimization mods ----
    /** Remove vanilla wander/idle goals we replace with flow-field nav (less CPU + less friction with
     *  Lithium). Keeps vanilla target acquisition + melee. RISKY: test before enabling. Default OFF. */
    public static boolean suppressVanillaWander = false;

    /** Treat mods that modify zombie AI behaviour as INCOMPATIBLE: hard-stop on detection. Default ON
     *  (we already drive zombie AI; a second AI mod would fight us). Set false to only warn. */
    public static boolean failOnAiConflict = true;

    // ---- Sound perception (Phase 4) ----
    /** Master toggle for sound perception. */
    public static boolean soundEnabled = true;
    /** Base hearing radius (blocks) for a normal sound. */
    public static double soundBaseRadius = 24.0;
    /** Multiplier on radius for loud sounds (block break). */
    public static double soundLoudMultiplier = 2.0;
    /** Minimum per-tick player movement (blocks) to emit a footstep sound. */
    public static double soundMoveThreshold = 0.08;
    /** Distance (blocks) at which a zombie considers it has reached the sound source. */
    public static double soundArriveDistance = 2.5;
}
