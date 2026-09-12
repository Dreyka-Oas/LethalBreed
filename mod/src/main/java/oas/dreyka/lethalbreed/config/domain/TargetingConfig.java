package oas.dreyka.lethalbreed.config.domain;

/**
 * Target acquisition (any living entity, not just players), coexistence with optimization mods, and sound
 * perception used to locate prey behind walls.
 */
public final class TargetingConfig {
    private TargetingConfig() {}

    // ---- Targeting (any living entity, not just players) ----
    /** Detection radius (blocks) for acquiring a target entity. Larger = sees prey farther, costs more. */
    public static double targetDetectRadius = 40.0;
    // NOTE: a targetDetectVerticalRadius knob was tried here to shrink the broad-phase box, on the theory
    // that the target scan costs by swept volume. Measured: no gain (23.8us/call at 24 blocks vs 22.1 with
    // the full radius), because the cost is visiting the entities inside the box, not the box itself. It was
    // removed: a knob that changes what zombies can see and buys nothing is a net loss.
    // See TargetSelector.findNearest for what an actual fix would require.
    /** Require line of sight to acquire a target by VISION. Opaque blocks block sight, translucent ones
     *  (glass, ice, leaves) don't. A target behind a solid wall is found via sound instead, not sight. */
    public static boolean requireLineOfSight = true;
    /** Make zombies actually attack their acquired target (deal damage), not just approach it. */
    public static boolean attackAllTargets = true;
    /** Strip vanilla target-selection goals so our "nearest living entity" pick is authoritative: the
     *  zombie always retargets to the closest entity instead of vanilla re-locking onto the player. */
    public static boolean forceNearestTarget = true;
    /** Restrict targeting to PLAYERS only. When on, non-player living entities (villagers, animals, other
     *  mobs) are never acquired as targets. Default off = hunt any valid living entity. */
    public static boolean targetPlayersOnly = false;
    /** Short-term memory: once a target is lost (out of sight AND out of hearing), keep heading to its LAST
     *  known position for this many ticks before giving up (200 = 10s). Any live detection (a nearer/visible
     *  or heard entity) overrides the memory immediately: the nearest DETECTED target always wins. 0 = off. */
    public static int targetMemoryTicks = 200;
    /** Target stickiness: once committed to a target, don't switch to a newly-visible other unless the new one
     *  is CLOSER than (current distance ÷ this factor). >1 = sticky (1.5 = only switch when the other is ~33%
     *  nearer). Stops a zombie mid-dig from thrashing between two equally-far targets. While breaking a wall
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

    // Appended at the end of the holder on purpose: the file on a player's disk is written in declaration
    // order, so adding this anywhere else would shuffle every line under it in a config they may have
    // annotated or diffed.
    /** Tallest creature (blocks) a zombie will hunt. Anything above this is left alone, which is how giants
     *  and oversized modded mobs stay out of the horde's reach. Raise it to let them be hunted. */
    public static double targetMaxPreyHeight = 5.0;
    /** Distances differing by less than this many blocks count as equally close when ranking prey, so a
     *  horde does not all commit to one target over a few centimetres. Never goes below 0.05, see
     *  {@code TargetOrder}. */
    public static double targetTieBandBlocks = 2.0;
    /** Ceiling on the footstep radius multiplier, so a player falling fast or teleporting cannot emit one
     *  sound the whole dimension hears. */
    public static double soundStepRadiusMax = 2.0;
    /** How often the creature noise sweep runs, in ticks. Player footsteps and event distribution still run
     *  every tick; only the sweep over every nearby creature is throttled. */
    public static int soundScanIntervalTicks = 4;
}
