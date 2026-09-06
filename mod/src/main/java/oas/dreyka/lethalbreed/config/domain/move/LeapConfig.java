package oas.dreyka.lethalbreed.config.domain.move;

/**
 * The leap: when a zombie lunges at its target, and what happens once it lands on one.
 *
 * <p>Split off {@code CombatMoveConfig}, which had no room left under the 150-line budget. The field names
 * are untouched by the move and no alias is owed: an option's section in {@code config/oas/lethalbreed.json}
 * comes from {@code ConfigCategory.of(name)}, never from the class declaring it, so every {@code leap*}
 * name keeps the exact path it already has on disk.
 *
 * <p>What the move does decide is the ORDER inside that section, which is holder order then declaration
 * order. Hence the one rule this class imposes on its surroundings: it must stay directly before
 * {@code CombatMoveConfig} in {@code ConfigSchema.HOLDERS}, where its fields used to be declared, or the
 * Leap section of every config file already on a disk gets reshuffled on the next save.
 *
 * <p>It sits in {@code config.domain.move} rather than beside its parent because {@code config.domain} is
 * full at eight files.
 */
public final class LeapConfig {
    private LeapConfig() {}

    // ---- Leap / pounce ----
    /** Zombies occasionally lunge at a target to catch it. */
    public static boolean leapEnabled = true;
    /** Activations between possible leaps (per zombie). */
    public static int leapCooldownActivations = 12;
    /** Chance to leap on an eligible activation. */
    public static float leapChance = 0.4f;
    /** Leap only when the target is between these horizontal distances. */
    public static double leapMinRange = 2.5;
    public static double leapMaxRange = 8.0;
    /** Leap velocity. */
    public static double leapHorizontalSpeed = 0.55;
    public static double leapUpward = 0.42;
    /** Max absolute vertical offset (blocks) to target for a leap to fire (too high/low = no pounce). */
    public static double leapMaxVerticalDiff = 3.0;
    /** Horizontal distance (blocks) ahead a leap probes for solid ground before committing (never leap into a gap). */
    public static int leapLandingScanDist = 3;
    /** Vertical depth (blocks below foot level) the leap-landing probe scans for ground. */
    public static int leapLandingScanDepth = 3;

    // ---- Cling (ride the target once a leap connects) ----
    /** A zombie that comes down on its target holds onto it and gnaws instead of bouncing off. */
    public static boolean leapClingEnabled = true;
    /** Shortest and longest a cling lasts, in seconds. Each one rolls a duration between the two. */
    public static double leapClingMinSeconds = 2.0;
    public static double leapClingMaxSeconds = 5.0;
    /** Health removed per second of clinging. One point is deliberately small: the longest cling takes 5 of
     *  an unarmoured player's 20, so four of them at full length are needed to kill, and a plain zombie bite
     *  (2.5 to 4.5) still hurts more. The cling is meant to hold the player still, not to finish it. */
    public static double leapClingDamage = 1.0;
    /** Horizontal distance squared (blocks squared) to the target within which a landing counts as a hit.
     *  A zombie and a player are both 0.6 blocks wide, so their hitboxes meet at 0.6 blocks between centres.
     *  One square block leaves the rest as slack for the ground a leap covers in a single tick. */
    public static double leapClingReachSq = 1.0;
    /** How far (blocks) below the target the zombie may still be and cling anyway. Small on purpose: the
     *  zombie is meant to land on top of its prey, not to reach up at it from the floor. */
    public static double leapClingMaxDropBelow = 1.0;
    /** Activations before the same zombie may cling again. Twenty-four is six seconds at the default
     *  five-tick bucket, longer than the longest cling, so one zombie alone cannot pin a player forever. */
    public static int leapClingCooldownActivations = 24;
}
