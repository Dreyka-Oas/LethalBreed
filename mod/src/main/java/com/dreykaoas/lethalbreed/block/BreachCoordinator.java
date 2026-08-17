package com.dreykaoas.lethalbreed.block;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Focus-fire coordination: makes a nearby group of zombies concentrate on ONE breach column at a time instead
 * of each chipping its own front block (so none ever finishes and the wall never opens). A zombie about to
 * break consults {@link #resolve}: if a compatible breach already exists nearby it ADOPTS it (and the caller
 * steers the zombie toward that breach's approach side so the whole cluster funnels to one hole); otherwise it
 * registers its own front column as a new breach, capped at {@code maxConcurrentBreaches} per cluster. One
 * instance per dimension (held by {@code WorldAiContext}); {@link #tick} drops finished/abandoned breaches.
 */
public final class BreachCoordinator {

    /** The column the zombie should break, and the cell to approach it from (the attacker/funnel side). */
    public record BreachTarget(BlockPos column, BlockPos approach) {}

    private static final class Breach {
        final BlockPos column;   // feet cell of the column being broken (the shared breach)
        final BlockPos approach; // where the first breaker attacks from — the side the cluster funnels to
        final BlockPos target;   // the hunt target this breach serves, so unrelated walls don't merge
        long lastTick;

        Breach(BlockPos column, BlockPos approach, BlockPos target, long now) {
            this.column = column;
            this.approach = approach;
            this.target = target;
            this.lastTick = now;
        }
    }

    private final Map<Long, Breach> breaches = new HashMap<>();
    private long now;

    /**
     * Decide which breach column this zombie should work toward {@code target}. Adopts the nearest existing
     * breach within {@code breachRadius} serving roughly the same target once the cluster is at its breach cap;
     * otherwise starts a new breach at {@code forwardColumn}. {@code approach} is the zombie's current cell.
     */
    public BreachTarget resolve(BlockPos forwardColumn, BlockPos approach, BlockPos target, boolean committed) {
        if (!CombatMoveConfig.breachEnabled || target == null) {
            return new BreachTarget(forwardColumn, approach); // legacy: each zombie breaks its own front column
        }
        if (committed) {
            // Already breaking this column — ANCHOR it (register/refresh) and stay put; the whole cluster
            // converges on ME instead of me being dragged off to a neighbour's breach mid-break.
            Breach b = breaches.get(forwardColumn.asLong());
            if (b == null) {
                b = new Breach(forwardColumn, approach, target, now);
                breaches.put(forwardColumn.asLong(), b);
            }
            b.lastTick = now;
            return new BreachTarget(forwardColumn, approach);
        }
        double r2 = CombatMoveConfig.breachRadius * CombatMoveConfig.breachRadius;
        double tr2 = 4.0 * r2; // same cluster if served targets are within ~2R of each other
        Breach nearest = null;
        double bestD = Double.MAX_VALUE;
        int nearby = 0;
        for (Breach b : breaches.values()) {
            double d = b.column.distSqr(forwardColumn);
            if (d > r2 || b.target.distSqr(target) > tr2) {
                continue;
            }
            nearby++;
            if (d < bestD) {
                bestD = d;
                nearest = b;
            }
        }
        if (nearest != null && nearest.column.equals(forwardColumn)) {
            nearest.lastTick = now;
            return new BreachTarget(nearest.column, nearest.approach); // I'm already on the shared column
        }
        if (nearest != null && nearby >= CombatMoveConfig.maxConcurrentBreaches) {
            nearest.lastTick = now;
            return new BreachTarget(nearest.column, nearest.approach); // at the cap → join the nearest breach
        }
        // Room for another breach (or none nearby) → register my front column as a new one.
        Breach nb = new Breach(forwardColumn, approach, target, now);
        breaches.put(forwardColumn.asLong(), nb);
        return new BreachTarget(forwardColumn, approach);
    }

    /** Drop breaches nobody has worked within the grace window, or whose column is already broken through. */
    public void tick(ServerLevel level, long tick) {
        now = tick;
        if (breaches.isEmpty()) {
            return;
        }
        long grace = CombatMoveConfig.breachGraceTicks;
        Iterator<Map.Entry<Long, Breach>> it = breaches.entrySet().iterator();
        while (it.hasNext()) {
            Breach b = it.next().getValue();
            if (now - b.lastTick > grace || !level.getBlockState(b.column).blocksMotion()) {
                it.remove();
            }
        }
    }
}
