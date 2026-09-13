package oas.dreyka.lethalbreed.config.bounds.engine;

import oas.dreyka.lethalbreed.config.BoundsRegistrar;

/**
 * Clamp ranges for the flow-field and pathing options (Compute / Pathing / Climb).
 *
 * <p>{@code ConfigBoundsTest} fails the build if a numeric option loses its bound.
 */
public final class FlowBounds {
    private FlowBounds() {}

    public static void register(BoundsRegistrar r) {
        r.b("flowCpuThreads", 0, 256);
        r.b("gpuWorkgroupSize", 0, 1024);
        r.b("gpuDeviceIndex", -1, 64);
        r.b("flowRecomputeInterval", 1, 6000);
        r.b("flowResampleOnMoveDist", 0, 1024);
        r.b("flowMargin", 0, 256);
        r.b("flowMaxGrid", 1, 512);
        r.b("flowVerticalTolerance", 0, 64);
        r.b("flowWaypointStep", 1, 64);
        r.b("navSpeed", 0, 10);
        r.b("navYThreshold", 0, 64);
        r.b("flowBreakCost", 0, 100_000);
        r.b("flowBuildCost", 0, 100_000);
        r.b("flowOrthoCost", 1, 1000);
        r.b("flowDiagonalCost", 1, 1000);
        r.b("climbThreshold", 0, 64);
        r.b("climbHorizRadius", 0, 64);
        r.b("climbGiveUpCooldown", 0, 1000);
        r.b("pillarMaxHeight", 1, 256);
        r.b("pillarJumpPower", 0, 2);

    }
}
