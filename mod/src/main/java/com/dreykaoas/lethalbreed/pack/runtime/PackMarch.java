package com.dreykaoas.lethalbreed.pack.runtime;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.config.domain.PackConfig;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.pack.PackAdvance;
import com.dreykaoas.lethalbreed.pack.PackManager;
import com.dreykaoas.lethalbreed.pack.PackState;
import com.dreykaoas.lethalbreed.pack.PackWander;

import net.minecraft.server.level.ServerLevel;

/**
 * Moves a materialised pack: picks the destination when there is none, plants the shared waypoint, and
 * gives up on a route nothing can reach.
 *
 * <p><b>The waypoint is one point for the whole pack</b>, planted {@code packMarchLead} blocks ahead of the
 * centroid, not a per-member projection. Two reasons, both load-bearing. It keeps every member's target
 * within the LOD tiers, so nobody freezes while walking. And it keeps the pack a pack: individual
 * projections would also scatter the targets that {@code BreachCoordinator} groups on (it merges breaches
 * whose targets are within 12 blocks), so the moment one member aggroed, twenty separate breaches would open
 * and none would ever finish.
 *
 * <p>Members that have a real target are skipped: aggro is sovereign. Sleepers are skipped too — day-sleep
 * beats migration, so a pack simply stops at dawn.
 */
public final class PackMarch {
    private PackMarch() {}

    /** How often the headway test is allowed to fire. Long enough that a walking pack visibly closes on
     *  its destination between two checks, short enough that a pack pinned against a wall gives up within
     *  packStuckActivations x this, i.e. a few seconds rather than a night. */
    private static final int HEADWAY_CHECK_TICKS = 40;

    public static void tick(ServerLevel level, PackManager manager, PackState pack, long gameTime) {
        if (!PackConfig.packMigrationEnabled) {
            return;
        }
        if (!PackConfig.packMigrateAtDay && level.isBrightOutside()) {
            // Daylight halt. Members keep whatever waypoint they hold until mood puts them to sleep, which
            // clears it via the FROZEN path — replanting here would fight the day-sleep for the whole day.
            return;
        }
        if (gameTime < pack.dwellUntil) {
            return;
        }
        if (arrivedOrStuck(pack, gameTime)) {
            chooseDestination(level, pack, gameTime);
        }
        plantWaypoint(pack, gameTime);
    }

    /** True when this pack needs a new destination — either it got there, or it cannot get there at all. */
    private static boolean arrivedOrStuck(PackState pack, long gameTime) {
        double dx = pack.destX - pack.x;
        double dz = pack.destZ - pack.z;
        boolean arrived = Math.sqrt(dx * dx + dz * dz) <= PackConfig.packArriveDistance;
        if (arrived) {
            pack.stuckActivations = 0;
            return true;
        }
        // No headway is the only signal a migration gets that the way is blocked: the pack does not break
        // blocks, so a wall with no way round would otherwise hold it against the stone forever.
        if (pack.stuckActivations >= PackConfig.packStuckActivations) {
            pack.stuckActivations = 0;
            return true;
        }
        return false;
    }

    private static void chooseDestination(ServerLevel level, PackState pack, long gameTime) {
        PackDestinationPick.pick(level, pack);
        pack.dwellUntil = gameTime + dwell(pack);
        // A fresh destination is further away than the old one by construction, so carrying the previous
        // distance over would score the very first check as "no headway" and start the stuck counter at 1.
        pack.lastDistToDest = Double.MAX_VALUE;
        pack.lastAdvanceTick = gameTime;
    }

    private static long dwell(PackState pack) {
        int jitter = Math.max(0, PackConfig.packDwellJitterTicks);
        long spread = jitter == 0 ? 0 : Math.floorMod(pack.seed >> 16, 2L * jitter) - jitter;
        return Math.max(0, PackConfig.packDwellTicks + spread);
    }

    /** Plant the shared waypoint and note whether the pack made any headway since the last CHECK. */
    private static void plantWaypoint(PackState pack, long gameTime) {
        double dx = pack.destX - pack.x;
        double dz = pack.destZ - pack.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        double lead = Math.min(PackConfig.packMarchLead, Math.max(1.0, d));
        double wx = d <= 1.0e-6 ? pack.x : pack.x + dx / d * lead;
        double wz = d <= 1.0e-6 ? pack.z : pack.z + dz / d * lead;

        // Headway is judged on a fixed interval, NOT once per visit. A pack is visited up to once a tick,
        // and a walking centroid closes on the order of 0.02 blocks in a tick — an order of magnitude under
        // HEADWAY_EPSILON. Testing per visit therefore scored a perfectly healthy march as "no headway"
        // almost every tick, tripped packStuckActivations within a second, and made the pack re-roll its
        // destination over and over: measured at 11 blocks covered in 780 ticks where the walk alone should
        // manage several times that. The interval is what makes the epsilon mean something.
        if (gameTime - pack.lastAdvanceTick >= HEADWAY_CHECK_TICKS) {
            if (d >= pack.lastDistToDest - PackAdvance.HEADWAY_EPSILON) {
                pack.stuckActivations++;
            } else {
                pack.stuckActivations = 0;
            }
            pack.lastDistToDest = d;
            pack.lastAdvanceTick = gameTime;
        }

        for (int i = 0; i < pack.liveIds.size(); i++) {
            SmartZombie m = GameState.REGISTRY.get(pack.liveIds.getInt(i));
            if (m == null || !m.isValid() || m.pursuit().targetEntity() != null || m.mood().isSleeping()) {
                continue;
            }
            // Y comes from the member itself: the pack has no meaningful height, and navigation resolves the
            // vertical anyway. Feeding a pack-level Y would aim half the members into the ground.
            m.pursuit().pack().setWaypoint(wx, m.y(), wz);
        }
    }
}
