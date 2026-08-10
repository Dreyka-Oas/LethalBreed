package com.dreykaoas.lethalbreed.config.domain;

/**
 * Pack instinct and migration: how loose zombies clump into packs, and how a pack drifts across the world.
 *
 * <p><b>No option here may ever be derived from, or compared against, a player position.</b> A pack picks
 * where to go from its own heading and a seeded random walk — never from where anyone is standing.
 * {@code PackNoPlayerAccessTest} fails the build if anything under {@code pack/} reaches for a player.
 */
public final class PackConfig {
    private PackConfig() {}

    // ---- Instinct de groupe ----
    /** Master switch. Off → no pack is ever formed and the per-zombie decision returns immediately. */
    public static boolean packEnabled = true;
    /** A loose zombie joins a pack whose centroid is within this many blocks. */
    public static double packCohesionRadius = 24.0;
    /** A member drifting beyond this from its centroid starts accruing stray activations. Deliberately far
     *  above packCohesionRadius (ratio ~1.66): a zombie walking around a wall must not lose its pack. */
    public static double packBreakRadius = 40.0;
    /** Zombies needed in the neighbourhood before a brand-new pack forms out of nothing. */
    public static int packFormMinSize = 3;
    /** Below this total membership a pack dissolves (after the grace period). */
    public static int packMinSize = 2;
    /** Hard cap on members. Also caps the Necromancer's summon when it is itself in a pack — otherwise a
     *  pack with one grows without bound, since nothing in this mod ever despawns. */
    public static int packMaxSize = 24;
    /** Neighbours needed before a lone zombie even considers a pack. 0 would make every zombie a candidate
     *  every activation for nothing — this is the "if there is nobody around it is pointless" short-circuit. */
    public static int packMinNeighbours = 1;
    /** Consecutive out-of-range activations before a member actually leaves. Pure hysteresis. */
    public static int packStrayActivations = 3;
    /** A zombie takes a pack decision one activation out of this many. The main CPU dial. */
    public static int packDecisionDivisor = 8;
    /** Max neighbours inspected per decision. Bounds the cost in a dense horde. */
    public static int packScanCap = 16;
    /** Packs visited per server tick, round-robin (centroid, merge probe, dissolve check). */
    public static int packsPerTick = 4;
    /** Two pack centroids closer than this are merge candidates. */
    public static double packMergeRadius = 32.0;
    /** Minimum dot product of the two headings for a merge. Without it, packs crossing in opposite
     *  directions would still fuse and the whole world population collapses into one mass. */
    public static double packMergeHeadingDot = 0.5;
    /** How long a pack may stay under packMinSize before it is dissolved. */
    public static int packDissolveGraceTicks = 200;

    // ---- Migration ----
    /** Off → packs form and stay cohesive but never pick a destination. */
    public static boolean packMigrationEnabled = true;
    /** Blocks per tick for a pack simulated coarsely, off-view. Roughly 40 % of a zombie's walk speed, to
     *  pay back the terrain detours a straight line never takes. */
    public static double packVirtualSpeed = 0.09;
    /** Shortest leg of the random walk, in blocks. */
    public static int packLegMin = 96;
    /** Longest leg of the random walk, in blocks. */
    public static int packLegMax = 384;
    /** Max heading change per leg. 0 → dead straight forever. Bounding it is what makes the path read as a
     *  migration rather than as brownian motion. */
    public static double packTurnDegrees = 45.0;
    /** Pause on arrival before choosing the next destination. */
    public static int packDwellTicks = 200;
    /** Random spread applied to the dwell, so packs do not all move in lockstep. */
    public static int packDwellJitterTicks = 100;
    /** How close the pack centroid must get to count as arrived. */
    public static double packArriveDistance = 8.0;
    /** Distance ahead of the centroid where the shared march waypoint is planted.
     *  <p>This is load-bearing, not cosmetic: LODManager classifies on the distance to the point being
     *  walked to, so aiming members at a destination hundreds of blocks away would push them past lodLow
     *  and FREEZE them instead of moving them. Keep well under lodHigh/lodMedium. */
    public static double packMarchLead = 24.0;
    /** Activations a pack may make no headway before it gives up and picks a new destination. This is how
     *  "walk around the obstacle" degrades gracefully when there is simply no path. */
    public static int packStuckActivations = 8;
    /** Let packs keep migrating in daylight. Off by default: the mod's day-sleep wins, so packs travel by
     *  night and do not burn under the sun below the immunity phase. */
    public static boolean packMigrateAtDay = false;

    // ---- Virtualisation hors-vue ----
    /** Dematerialise packs nobody can see, and bring them back on approach.
     *  <p>Its own switch rather than a corner of packMigrationEnabled: this is the one part of the system
     *  that creates and destroys entities, so an operator who suspects it must be able to stop it alone
     *  without also stopping migration. Off leaves packs fully materialised, which simply costs more. */
    public static boolean packVirtualEnabled = true;
    /** Ticks between two materialisation sweeps. The sweep is O(packs), not O(zombies). */
    public static int packMaterializeInterval = 20;
    /** Consecutive sweeps a pack must be found not entity-ticking before it is dematerialised.
     *  <p>A grace period, not a timer: chunk unloading has been measured in this project at 2, 35, 272 and
     *  once over 1200 ticks, so a single observation proves nothing. */
    public static int packDematGraceTicks = 40;
    /** Radius over which restored members are scattered, so a pack does not come back in one column. */
    public static int packSpawnSpread = 6;
    /** How many sweeps a ghost may fail to restore before the pack gives up on it. */
    public static int packMaterializeRetries = 5;
    /** How far from its pack a returning member may be and still re-join it. */
    public static double packRejoinRadius = 64.0;

    /** Log pack formation, merges and destination changes. Noisy; dev only. */
    public static boolean debugPacks = false;
}
