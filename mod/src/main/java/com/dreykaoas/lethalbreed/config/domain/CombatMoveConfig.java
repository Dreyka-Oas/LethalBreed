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
    /** Max absolute vertical offset (blocks) to target for a leap to fire (too high/low = no pounce). */
    public static double leapMaxVerticalDiff = 3.0;
    /** Horizontal distance (blocks) ahead a leap probes for solid ground before committing (never leap into a gap). */
    public static int leapLandingScanDist = 3;
    /** Vertical depth (blocks below foot level) the leap-landing probe scans for ground. */
    public static int leapLandingScanDepth = 3;

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
    /** Vertical offset (blocks) below the zombie past which a target counts as "submerged below" (triggers a dive). */
    public static double waterSubmergeOffset = 0.5;
    /** Horizontal distance (blocks) to the target within which the swim drive stops pushing. */
    public static double waterArriveDistance = 0.6;
    /** Swim velocity smoothing: fraction of the DESIRED velocity blended in each tick (the rest keeps current). */
    public static double waterVelocityBlend = 0.4;
    /** Upward impulse (blocks/tick) used to hop/stack at the water surface when a solid edge blocks progress. */
    public static double waterSurfaceJump = 0.42;

    // ---- Climb pacing ----
    /** Activations of no horizontal progress before the zombie is "stuck" and may break/build/pillar.
     *  Until then it just walks (vanilla auto-steps 1 block + jumps 1-wide gaps) — no needless block ops. */
    public static int stuckActivations = 2;
    /** Min horizontal-distance-squared improvement (blocks²) that still counts as "making progress" toward the
     *  target. Below this, the activation is counted toward {@link #stuckActivations}. 0.25 = 0.5 blocks; raise
     *  to demand more progress (acts/breaks sooner), lower to be more patient before block ops. */
    public static double stuckProgressEpsilon = 0.25;
    /** Safety cap: abort a pillar climb if it fails to gain a full block within this many activations on the
     *  CURRENT rung — stops a zombie jumping in place forever when a support can't land (queue full, ceiling,
     *  or a sideways-blocked arc). A healthy pillar gains a rung every few ticks and never trips this. */
    public static int climbJumpMaxAge = 16;
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
    /** Target within this vertical distance (blocks) → the pillar climb is done and hops off toward the target. */
    public static double pillarFinishHeight = 1.0;
    /** Horizontal speed (blocks/tick) of the hop off the top of a finished pillar. */
    public static double pillarFinishSpeed = 0.4;
    /** Upward impulse (blocks/tick) of the hop off the top of a finished pillar. */
    public static double pillarFinishJump = 0.42;
    /** Target within this horizontal distance (blocks) counts as "directly below" → dig straight down after it. */
    public static double descendDirectlyBelowRadius = 1.5;

    // ---- Progressive breaking (player-like) ----
    /** Break progress added per tick (divided by block hardness). Lower = slower mining. */
    public static float breakProgressPerTick = 0.04f;
    /** Ticks a break survives without being re-requested before it's abandoned (crack clears). */
    public static long breakGraceTicks = 10L;

    // ---- Focus-fire breach coordination (concentrate a group on ONE block at a time) ----
    /** Zombies near each other coordinate to break a SINGLE breach column at a time (converging on it) rather
     *  than each chipping its own front block so none ever finishes. false = legacy each-breaks-its-own. */
    public static boolean breachEnabled = true;
    /** Cluster radius (blocks): a zombie about to break adopts an existing breach within this range that serves
     *  the same target, instead of starting a new one. Larger = the whole crowd funnels to one hole. */
    public static double breachRadius = 6.0;
    /** Max distinct breach columns allowed at once within one cluster/target. 1 = strict one-block-at-a-time. */
    public static int maxConcurrentBreaches = 1;
    /** Ticks a breach survives without any zombie working it before it's dropped (frees the slot for the next). */
    public static long breachGraceTicks = 20L;
    /** Concentration speed: multiple zombies breaking the SAME block break it faster. Progress is multiplied by
     *  {@code 1 + (breakers-1) * this}, capped by {@link #breakConcentrationCap}. 0 = no bonus (legacy fixed rate). */
    public static double breakConcentrationPerBreaker = 0.6;
    /** Hard cap on the concentration speed multiplier so a huge pile of zombies can't shred blocks instantly. */
    public static double breakConcentrationCap = 4.0;

    // ---- Block ops (Phase 3) ----
    /** Master toggle for ALL world mutation (breaks + placements/bridge/pillar). false = pure vanilla
     *  pathing, zero block ops. Gated at BreakManager.request and BlockOperationQueue.enqueuePlace. */
    public static boolean blockOpsEnabled = true;
    /** Max placement ops applied per tick per dimension (the place queue; breaks run via BreakManager). */
    public static int blockOpsPerTick = 20;
    /** Pending-op queue cap; ops past this are dropped until it drains. */
    public static int blockOpsQueueCap = 500;
    /** Anti-TPS cap: max distinct blocks being progressively broken at once (per dimension). New breaks
     *  past this are ignored until an active one finishes. */
    public static int maxConcurrentBreaks = 64;
    /** Never break blocks with a block-entity (chests, furnaces, spawners, beds, etc.). Anti-grief. */
    public static boolean breakProtectBlockEntities = true;
    /** Whether broken blocks drop their items. false = no item-entity spam during a raid. */
    public static boolean breakDropsItems = true;
    /** Blocks with getDestroySpeed above this are treated as unbreakable (excludes obsidian etc.). */
    public static float breakMaxHardness = 30.0f;
    /** Ticks before zombie-placed dirt is auto-removed (no drop). 600 = 30s. */
    public static long placedBlockLifetimeTicks = 600L;
}
