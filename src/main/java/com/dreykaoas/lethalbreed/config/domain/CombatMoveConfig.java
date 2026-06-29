package com.dreykaoas.lethalbreed.config.domain;

/**
 * Movement combat: leap/pounce, water swim/dive/float, climb pacing, descend/safe-drop, melee stop range
 * and the progressive block breaking / block-ops budget.
 */
public final class CombatMoveConfig {
    private CombatMoveConfig() {}

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

    /** Max vertical blocks a zombie breaks to pass an obstacle (size-aware, ceil of its height). */
    public static int maxBreakHeight = 4;

    // ---- Water behaviour ----
    /** Make zombies FLOAT and swim at the water surface (like a player/drowned) instead of sinking and
     *  walking along the bottom — so they keep pursuing across water. Adds a vanilla {@code FloatGoal}
     *  (which land mobs have but zombies lack). Read at entity construction (registerGoals). */
    public static boolean floatInWater = true;
    /** Gentle upward velocity per tick used to surface when submerged (small so they don't pop out). */
    public static double waterRiseSpeed = 0.04;
    /** Downward velocity per tick when diving after a target that is submerged below. */
    public static double waterDiveSpeed = 0.10;
    /** Horizontal swim speed per tick toward the target while in water (driven directly, not via nav, so
     *  the zombie heads straight at the target instead of circling). Kept modest — vanilla swim pace. */
    public static double waterSwimSpeed = 0.06;

    // ---- Climb pacing ----
    /** Activations to wait between pillar steps (higher = slower, more natural climb). */
    public static int climbCooldown = 2;
    /** Activations of no horizontal progress before the zombie is "stuck" and may break/build/pillar.
     *  Until then it just walks (vanilla auto-steps 1 block + jumps 1-wide gaps) — no needless block ops. */
    public static int stuckActivations = 2;
    /** Max ticks a pillar-jump waits to gain height before aborting (jump arc length). */
    public static int climbJumpMaxAge = 16;
    /** Upward velocity of a pillar jump (0.42 ≈ vanilla player jump, ~1.25 blocks high). */
    public static double climbJumpVelocity = 0.42;
    /** Target this many blocks BELOW (and stuck at a ledge) → dig straight down to descend safely. */
    public static double descendThreshold = 2.0;
    /** A drop this many blocks or shorter is walked/dropped down for free instead of being carved into a
     *  staircase or filled with dirt (vanilla: a fall &lt;4 blocks deals no damage). Stops zombies from
     *  breaking a floor block — or bridging a ledge — they could simply step off and pass. */
    public static int safeDropBlocks = 3;
    /** Cancel fall damage for our smart zombies. OFF: realistic damage; they avoid falls by digging down. */
    public static boolean preventFallDamage = false;
    /** Once the target is within this horizontal distance AND in line of sight, the zombie has "arrived"
     *  and can melee — so it does NO block ops (no digging/bridging/breaking next to a reachable target). */
    public static double meleeStopRange = 2.0;
    /** Max vertical gap (blocks) for the arrived/melee stop to apply (a target far above/below isn't a
     *  melee, so block ops may still run to climb/descend to it). */
    public static double meleeStopHeight = 1.5;

    // ---- Progressive breaking (player-like) ----
    /** Break progress added per tick (divided by block hardness). Lower = slower mining. */
    public static float breakProgressPerTick = 0.04f;
    /** Ticks a break survives without being re-requested before it's abandoned (crack clears). */
    public static long breakGraceTicks = 10L;

    // ---- Block ops (Phase 3) ----
    /** Max world-mutating ops applied per tick per dimension (breaks prioritized over placements). */
    public static int blockOpsPerTick = 20;
    /** Pending-op queue cap; ops past this are dropped until it drains. */
    public static int blockOpsQueueCap = 500;
    /** Blocks with getDestroySpeed above this are treated as unbreakable (excludes obsidian etc.). */
    public static float breakMaxHardness = 30.0f;
    /** Ticks before zombie-placed dirt is auto-removed (no drop). 600 = 30s. */
    public static long placedBlockLifetimeTicks = 600L;
}
