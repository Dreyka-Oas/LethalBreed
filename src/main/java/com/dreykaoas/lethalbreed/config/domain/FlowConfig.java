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
    /** OpenCL work-group (local) size for the GPU solve. 0 = let the driver pick (safe default). Set to a
     *  power of two the device supports (e.g. 64/128/256) to tune throughput; the global range is rounded
     *  up to a multiple and the kernel bounds-checks the tail, so an over-large value is harmless. */
    public static int gpuWorkgroupSize = 0;
    /** Minimum grid area (cells = width × depth) before the GPU solver is used; smaller fields solve on the
     *  CPU instead, where the GPU's buffer upload/round-trip overhead would outweigh its throughput. The CPU
     *  and GPU solvers produce the identical cost field, so this only trades latency, never correctness.
     *  0 = always use the GPU when available. Ignored when {@link #gpuAutoCalibrate}.
     *  <p>Default 1024 (≈32²): with the batched GPU convergence check (one readback per 16 passes, not per
     *  pass) a boot benchmark on an AMD RX 9060 XT measured the GPU FASTER than the parallel CPU solver from
     *  ~24² upward (e.g. 192²: 6.3 ms GPU vs 15.4 ms CPU), so only tiny fields stay on the CPU. Enable
     *  {@link #gpuAutoCalibrate} to measure the exact crossover on the host hardware. */
    public static int gpuMinCells = 1024;
    /** Measure the CPU↔GPU crossover once at server start (micro-benchmark of both solvers at a range of
     *  grid sizes) and use that as the GPU threshold instead of the fixed {@link #gpuMinCells}. Adds a brief
     *  one-off boot cost on this exact machine; the result is logged. Off = use the manual gpuMinCells. */
    public static boolean gpuAutoCalibrate = false;
    /** Which OpenCL GPU to use, as an index into the detected-GPU list (logged at boot). -1 = auto (prefer an
     *  AMD/Radeon device, else the first GPU). Out-of-range falls back to auto. Only matters with >1 GPU. */
    public static int gpuDeviceIndex = -1;

    // ---- Flow field (Phase 2) ----
    /** Ticks between flow-field recomputes per dimension. */
    public static int flowRecomputeInterval = 10;
    /** Skip a scheduled recompute when the players' focus centre has moved less than this many blocks since
     *  the last solve (saves the solve while players stand still). 0 = always recompute on interval. WARNING:
     *  a stale field won't reflect blocks broken/placed while the focus is still, so keep this small. */
    public static double flowResampleOnMoveDist = 0.0;
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
    /** Per-step path cost for an orthogonal (N/S/E/W) move in the flow-field solve. Scaled integer: 10 ≈ 1.0
     *  block. Must stay below {@link #flowDiagonalCost} or diagonals are never preferred. CPU and GPU read
     *  the same value so both solvers produce the identical field. */
    public static int flowOrthoCost = 10;
    /** Per-step path cost for a diagonal move (≈ orthogonal × √2). 14 ≈ 1.41. Raise above 14 to make zombies
     *  prefer straight orthogonal approaches; lower toward {@link #flowOrthoCost} for more diagonal shortcuts. */
    public static int flowDiagonalCost = 14;
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
