package oas.dreyka.lethalbreed.util.target;

import oas.dreyka.lethalbreed.config.domain.TargetingConfig;
import oas.dreyka.lethalbreed.spatial.TargetIndex;
import oas.dreyka.lethalbreed.probe.DevProbe;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks what a zombie hunts: the nearest living entity, EXCEPT bosses (Ender Dragon, Wither) and other
 * zombies (so they don't gridlock targeting each other). Creative/spectator players are excluded so
 * you can observe.
 */
public final class TargetSelector {
    private TargetSelector() {}

    /** Sticky variant: prefer the already-committed {@code current} target over a marginally-closer new one
     *  (see {@link TargetingConfig#targetSwitchMargin}). While a zombie digs through a wall its prey is out of
     *  sight, so a plain nearest-visible pick would flip away and abandon the block. */
    public static LivingEntity findNearest(ServerLevel level, Mob self, double radius, LivingEntity current,
                                           TargetIndex index) {
        LivingEntity best = findNearest(level, self, radius, index);
        double margin = TargetingConfig.targetSwitchMargin;
        if (margin <= 1.0 || current == null || current == self || !TargetFilter.isValid(self, current)) {
            return best;
        }
        double curSq = self.distanceToSqr(current);
        if (curSq > radius * radius) {
            return best; // committed target left the detection radius → let the fresh pick take over
        }
        if (best == null) {
            return current; // nothing else detected → stay committed and keep digging toward it
        }
        // Keep current unless the new candidate is closer than current ÷ margin (compare in squared space).
        return curSq <= self.distanceToSqr(best) * margin * margin ? current : best;
    }

    /** Nearest valid living target the zombie can SEE within {@code radius}, or null. VISION ONLY: a live
     *  combat target is acquired by line of sight alone. Hearing is handled by the sound bus, which feeds
     *  short-term MEMORY so the zombie walks to investigate the SPOT; a seen target overrides that memory in
     *  {@code LodManager} the instant something comes into view. See {@link Perception}. */
    public static LivingEntity findNearest(ServerLevel level, Mob self, double radius, TargetIndex index) {
        boolean prof = DevProbe.on();
        long t0 = prof ? System.nanoTime() : 0L;
        List<LivingEntity> candidates = collectCandidates(level, self, radius, index);
        if (prof) {
            DevProbe.sink.stage(DevProbe.SCAN, System.nanoTime() - t0);
        }
        int n = candidates.size();
        if (n == 0) {
            return null; // nothing in range: skip the shuffle, the sort and the radius pass entirely
        }
        double radiusSq = radius * radius;
        if (n == 1) {
            // Overwhelmingly the common case once zombies are excluded. Ordering is meaningless for one
            // element, so go straight to the visibility test.
            LivingEntity only = candidates.get(0);
            if (self.distanceToSqr(only) > radiusSq) {
                return null;
            }
            long tl = prof ? System.nanoTime() : 0L;
            boolean seen = !TargetingConfig.requireLineOfSight || Perception.canSee(level, self, only);
            if (prof) {
                DevProbe.sink.stage(DevProbe.LOS, System.nanoTime() - tl);
            }
            return seen ? only : null;
        }
        long tOrder = prof ? System.nanoTime() : 0L;
        double[] distSq = TargetOrder.shuffleAndOrder(candidates, self);
        if (prof) {
            DevProbe.sink.stage(DevProbe.ORDER, System.nanoTime() - tOrder);
        }
        return nearestVisible(level, self, candidates, distSq, radiusSq, n, prof);
    }

    private static List<LivingEntity> collectCandidates(ServerLevel level, Mob self, double radius, TargetIndex index) {
        // Broad phase. MEASURED (StageProfiler, ~100 zombies): asking the world for every LivingEntity in an
        // 80-block box was ~50% of the whole reclassify stage, itself ~40% of the mod's tick time, because
        // it visits the entire horde only to have isValid reject Zombie on each one.
        //
        // Shrinking the box does NOT fix that, and the measurement says so: narrowing the vertical
        // extent to 24 blocks left the sweep at 23.8us/call against 22.1 without it. The cost is the
        // entities inside, not the volume. So the horde is simply never offered to the scan: prey lives
        // in the mod's own TargetIndex, and players (few, and far too important to risk a bookkeeping slip
        // hiding one) are read live from the level.
        List<LivingEntity> candidates = new ArrayList<>();
        if (index != null) {
            index.collectInto(candidates, self.getX(), self.getZ(), radius);
            for (Player p : level.players()) {
                candidates.add(p);
            }
            candidates.removeIf(e -> !TargetFilter.isValid(self, e));
        } else {
            // No index wired (unit tests, or a call path that predates it): fall back to the world scan.
            AABB box = self.getBoundingBox().inflate(radius);
            candidates = level.getEntitiesOfClass(LivingEntity.class, box, e -> TargetFilter.isValid(self, e));
        }
        return candidates;
    }

    private static LivingEntity nearestVisible(ServerLevel level, Mob self, List<LivingEntity> candidates,
                                                double[] distSq, double radiusSq, int n, boolean prof) {
        long tLos = prof ? System.nanoTime() : 0L;
        try {
            for (int i = 0; i < n; i++) {
                // SIGHT only: within the visual detect radius AND (if required) an unobstructed line of sight.
                // Iterated in distance order, so the first visible candidate is the nearest visible one.
                if (distSq[i] <= radiusSq
                        && (!TargetingConfig.requireLineOfSight || Perception.canSee(level, self, candidates.get(i)))) {
                    return candidates.get(i); // nearest seen, done
                }
            }
            return null;
        } finally {
            if (prof) {
                DevProbe.sink.stage(DevProbe.LOS, System.nanoTime() - tLos);
            }
        }
    }
}
