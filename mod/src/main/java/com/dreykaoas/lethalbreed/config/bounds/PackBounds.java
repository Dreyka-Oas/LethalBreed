package com.dreykaoas.lethalbreed.config.bounds;

import com.dreykaoas.lethalbreed.config.BoundsRegistrar;

/**
 * Clamp ranges for the pack instinct and migration options.
 *
 * <p>Split out of {@code ConfigBoundsTable}, which had grown to 305 append-only lines across nine unrelated
 * domains: a bound belongs next to the options it governs, and {@code ConfigBoundsTest} fails the build if
 * any numeric option loses one.
 */
public final class PackBounds {
    private PackBounds() {}

    public static void register(BoundsRegistrar r) {
        // ---- Pack instinct ----
        r.b("packCohesionRadius", 4, 128);
        r.b("packBreakRadius", 8, 256);
        r.b("packFormMinSize", 2, 32);
        r.b("packMinSize", 1, 32);
        r.b("packMaxSize", 2, 128);
        r.b("packMinNeighbours", 0, 32);
        r.b("packStrayActivations", 1, 64);
        r.b("packDecisionDivisor", 1, 64);
        r.b("packScanCap", 1, 64);
        r.b("packsPerTick", 1, 64);
        r.b("packMergeRadius", 4, 256);
        // A dot product only ever lives in [-1, 1]; anything outside would make the merge test constant.
        r.b("packMergeHeadingDot", -1, 1);
        r.b("packDissolveGraceTicks", 0, 72_000);

        // ---- Migration ----
        r.b("packVirtualSpeed", 0, 1);
        r.b("packLegMin", 16, 8192);
        r.b("packLegMax", 16, 8192);
        r.b("packTurnDegrees", 0, 180);
        r.b("packDwellTicks", 0, 72_000);
        r.b("packDwellJitterTicks", 0, 72_000);
        r.b("packArriveDistance", 1, 64);
        // Upper bound deliberately under SchedulerConfig.lodLow (128): a lead beyond the LOD tiers would
        // classify marching members as FROZEN and stop the migration dead.
        r.b("packMarchLead", 4, 128);
        r.b("packStuckActivations", 1, 128);

        // ---- Virtualisation ----  (packVirtualEnabled is a boolean: no bounds)
        r.b("packMaterializeInterval", 1, 1200);
        r.b("packDematGraceTicks", 0, 12_000);
        r.b("packSpawnSpread", 1, 32);
        r.b("packMaterializeRetries", 0, 64);
        r.b("packRejoinRadius", 8, 512);
    }
}
