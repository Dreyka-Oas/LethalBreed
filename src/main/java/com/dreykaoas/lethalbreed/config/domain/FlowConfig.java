package com.dreykaoas.lethalbreed.config.domain;

/**
 * Flow-field pathing (GPU/CPU solver, grid sizing, path costs) and wall-climb tuning used when following
 * the field toward an overhead target.
 */
public final class FlowConfig {
    private FlowConfig() {}

    // ---- GPU compute (Phase 6) ----
    /** Auto-use the OpenCL GPU solver for the flow field whenever an OpenCL GPU is present on the
     *  server. ON by default: if no GPU is detected, {@code GpuComputeManager.isAvailable()} is false
     *  and the CPU solver runs transparently. Acts as a kill-switch (set false to force CPU). */
    public static boolean useGpu = true;
    /** CPU flow-field solver threads when no GPU (the multi-core backup). 0 = auto = cores-2. */
    public static int flowCpuThreads = 0;

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
}
