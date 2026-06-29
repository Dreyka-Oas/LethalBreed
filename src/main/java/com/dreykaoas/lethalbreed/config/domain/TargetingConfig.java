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
    /** Require line of sight to acquire a target by VISION — opaque blocks block sight, translucent ones
     *  (glass, ice, leaves) don't. A target behind a solid wall is found via sound instead, not sight. */
    public static boolean requireLineOfSight = true;
    /** Make zombies actually attack their acquired target (deal damage), not just approach it. */
    public static boolean attackAllTargets = true;
    /** Strip vanilla target-selection goals so our "nearest living entity" pick is authoritative — the
     *  zombie always retargets to the closest entity instead of vanilla re-locking onto the player. */
    public static boolean forceNearestTarget = true;
    /** Short-term memory: once a target is lost (out of sight AND out of hearing), keep heading to its LAST
     *  known position for this many ticks before giving up (200 = 10s). Any live detection (a nearer/visible
     *  or heard entity) overrides the memory immediately — the nearest DETECTED target always wins. 0 = off. */
    public static int targetMemoryTicks = 200;

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
