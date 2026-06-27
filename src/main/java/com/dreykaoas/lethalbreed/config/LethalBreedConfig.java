package com.dreykaoas.lethalbreed.config;

/**
 * Lightweight runtime config. Defaults live here; a JSON loader (run/config/lethalbreed.json)
 * can override them later. Kept dependency-free for the Phase 1 build.
 */
public final class LethalBreedConfig {
    private LethalBreedConfig() {}

    /** Number of stagger buckets for the tick scheduler (1000 zombies / N = updates per tick). */
    public static int tickBuckets = 5;

    /** Spatial hash cell size in blocks (XZ). */
    public static int spatialCellSize = 8;

    /** LOD radii (blocks). Beyond {@link #lodFrozen} the mod stops ticking the zombie. */
    public static double lodHigh = 32.0;
    public static double lodMedium = 64.0;
    public static double lodLow = 128.0;
    public static double lodFrozen = 128.0;

    /** How often (ticks) to reclassify LOD. */
    public static int lodReclassifyInterval = 20;

    // ---- Distance-tier throttle (borrowed from Immersive Optimization) ----
    /** Scale per-zombie AI frequency by LOD so distant zombies cost less. */
    public static boolean throttleByLod = true;
    /** MEDIUM-LOD zombies run their AI 1 activation out of N. */
    public static int lodMediumTickDivisor = 2;
    /** LOW-LOD zombies run their AI 1 activation out of N. */
    public static int lodLowTickDivisor = 8;
    /** Min ticks between forced re-paths (reduces pathfinder churn; nav also re-paths when done). */
    public static int navReissueInterval = 4;

    /** How often (ticks) to emit the dev perf recap (100 ticks = 5s). 0 disables (default — no log spam). */
    public static int debugLogInterval = 0;

    // ---- Dev climb test (headless) ----
    /** Build a wall + villager-on-top + zombies arena on server start, for autonomous climb testing. */
    public static boolean devClimbTest = false;
    /** Log each targeting zombie's approach/climb state ([ClimbDbg] lines). Auto-enabled by the climb test. */
    public static boolean debugClimb = false;

    /** Radius (blocks) around the player used by the /lethalspawn dev command. */
    public static int devSpawnRadius = 16;

    // ---- GPU compute (Phase 6) ----
    /** Auto-use the OpenCL GPU solver for the flow field whenever an OpenCL GPU is present on the
     *  server. ON by default: if no GPU is detected, {@code GpuComputeManager.isAvailable()} is false
     *  and the CPU solver runs transparently. Acts as a kill-switch (set false to force CPU). */
    public static boolean useGpu = true;

    // ---- Flow field (Phase 2) ----
    /** Ticks between flow-field recomputes per dimension. */
    public static int flowRecomputeInterval = 10;
    /** Blocks of padding added around the players' bounding box. */
    public static int flowMargin = 24;
    /** Hard cap on the flow-field grid side (blocks) to bound compute cost. */
    public static int flowMaxGrid = 192;
    /** Vertical window (+/- blocks around focus Y) searched for a standable surface. */
    public static int flowVerticalTolerance = 4;
    /** How many blocks downhill the navigation waypoint is placed each update. */
    public static int flowWaypointStep = 6;
    /** Navigation speed modifier when following the flow field. */
    public static double navSpeed = 1.0;
    /** Extra path cost to route THROUGH a breakable block (vs detour). Lower = break more eagerly. */
    public static int flowBreakCost = 60;
    /** Extra path cost to BRIDGE a gap with dirt (vs detour). */
    public static int flowBuildCost = 100;
    /** Chase players in creative/spectator. Default false = they ignore you when not in survival. */
    public static boolean targetCreativePlayers = false;
    /** Build a staircase up when the target is at least this many blocks above the zombie. */
    public static double climbThreshold = 2.0;
    /** Climb ZONE: start scaling once stuck against a wall within this horizontal distance of an overhead
     *  target. Generous on purpose — the wall-climb tops out onto the ledge then walks to the target, so it
     *  no longer needs to be perfectly lined up. Too tight and zombies stall at a wall they can't quite reach. */
    public static double climbHorizRadius = 5.0;
    /** Give up a wall-climb after it has risen this many blocks without reaching the target — only a safety
     *  cap against scaling an endless wall toward an unreachable sky target. Generous: the wall-climb places
     *  no blocks, so a tall climb leaves nothing behind and never strands the zombie. */
    public static int maxClimbHeight = 24;
    /** Upward speed (blocks/tick) when a zombie scales a wall toward an overhead target (≈ vanilla ladder).
     *  Spider-style climb — NO blocks are placed, so nothing is ever left stranding the zombie. */
    public static double wallClimbSpeed = 0.2;
    /** Activations a zombie waits before re-attempting a wall it just failed to top (too tall) — stops it
     *  jittering up-and-down the same unreachable wall forever. */
    public static int climbGiveUpCooldown = 15;
    /** Give up a pillar-up after rising this many blocks without reaching the target (safety cap). */
    public static int pillarMaxHeight = 24;
    /** Upward velocity applied to launch each pillar jump (vanilla jump is ~0.42 → clears one block). */
    public static double pillarJumpPower = 0.42;

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

    // ---- World rules ----
    /** Force the overworld to stay daytime. */
    public static boolean forceDayTime = true;
    /** Time of day to hold (6000 = noon). */
    public static long forcedDayTime = 6000L;
    /** Keep the weather clear (no rain/thunder). */
    public static boolean clearWeather = true;

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

    // ---- Per-zombie variation (deterministic from UUID) ----
    /** Give each zombie slightly randomized size/strength/speed/leap (modest ranges). */
    public static boolean enableVariation = true;
    public static double varScaleMin = 0.85, varScaleMax = 1.25;   // body size
    public static double varSpeedMin = 0.9, varSpeedMax = 1.2;     // movement speed
    public static double varDamageMin = 0.85, varDamageMax = 1.3;  // attack damage
    public static double varLeapMin = 0.85, varLeapMax = 1.2;      // leap power

    // ---- Spawn control (Phase 1) ----
    /** Discard baby zombies on load. */
    public static boolean blockBabyZombies = true;
    /** Discard drowned on load (keeps the population to plain zombies). */
    public static boolean blockDrowned = true;
    /** Strip armor/weapons from zombies. OFF: they keep gear — a held weapon adds melee damage (vanilla)
     *  and a held tool speeds up their block breaking (see BreakManager). */
    public static boolean stripZombieEquipment = false;

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

    public static void load() {
        // Phase 1: defaults only. JSON override hook added in a later phase.
    }
}
