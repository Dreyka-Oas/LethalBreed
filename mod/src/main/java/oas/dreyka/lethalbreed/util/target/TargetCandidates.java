package oas.dreyka.lethalbreed.util.target;

import oas.dreyka.lethalbreed.spatial.TargetIndex;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * The broad phase of target acquisition: everything a zombie could plausibly hunt right now.
 *
 * <p>Split out of {@link TargetSelector}, which keeps the choosing. This half is where the measurements
 * are, and it reads better next to its own scratch buffer than buried in the middle of the pick.
 */
final class TargetCandidates {
    private TargetCandidates() {}

    /**
     * Reused across calls, for the same reason and under the same conditions as {@code TargetOrder}'s sort
     * scratch: server thread only, and the list is ordered and then walked before {@code findNearest}
     * returns. Nothing downstream keeps it.
     */
    private static final List<LivingEntity> SCRATCH = new ArrayList<>();

    static List<LivingEntity> collect(ServerLevel level, Mob self, double radius, TargetIndex index) {
        // MEASURED (StageProfiler, ~100 zombies): asking the world for every LivingEntity in an 80-block box
        // was ~50% of the whole reclassify stage, itself ~40% of the mod's tick time, because it visits the
        // entire horde only to have isValid reject Zombie on each one.
        //
        // Shrinking the box does NOT fix that, and the measurement says so: narrowing the vertical extent to
        // 24 blocks left the sweep at 23.8us/call against 22.1 without it. The cost is the entities inside,
        // not the volume. So the horde is simply never offered to the scan: prey lives in the mod's own
        // TargetIndex, and players (few, and far too important to risk a bookkeeping slip hiding one) are
        // read live from the level.
        if (index == null) {
            // No index wired (unit tests, or a call path that predates it): fall back to the world scan.
            AABB box = self.getBoundingBox().inflate(radius);
            return level.getEntitiesOfClass(LivingEntity.class, box, e -> TargetFilter.isValid(self, e));
        }
        List<LivingEntity> candidates = SCRATCH;
        candidates.clear();
        index.collectInto(candidates, self.getX(), self.getZ(), radius);
        // The index already narrows prey to the radius; the player list does not, and it is the whole
        // server's. A player further than the radius is dropped by the radius test in nearestVisible anyway,
        // so keeping it here only pays for it in the shuffle and the sort, once per zombie per activation.
        // Same squared 3D distance those two use, so the pick itself cannot move.
        double radiusSq = radius * radius;
        for (Player p : level.players()) {
            if (self.distanceToSqr(p) <= radiusSq) {
                candidates.add(p);
            }
        }
        compact(candidates, self);
        return candidates;
    }

    /** Drop everything this zombie may not hunt. A loop rather than removeIf: the predicate captures self,
     *  so it was one lambda per zombie per activation, and removeIf shifts the tail on every removal while
     *  this writes each survivor exactly once. */
    private static void compact(List<LivingEntity> candidates, Mob self) {
        int kept = 0;
        for (int i = 0; i < candidates.size(); i++) {
            LivingEntity e = candidates.get(i);
            if (TargetFilter.isValid(self, e)) {
                candidates.set(kept++, e);
            }
        }
        for (int i = candidates.size() - 1; i >= kept; i--) {
            candidates.remove(i); // from the end, so nothing is shifted
        }
    }
}
