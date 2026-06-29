package com.dreykaoas.lethalbreed.util;

import com.dreykaoas.lethalbreed.mixin.MobGoalsAccessor;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime strip/restore of a mob's vanilla target-selection goals so {@code forceNearestTarget} can be
 * toggled LIVE on already-spawned zombies — not only on freshly spawned ones. When a mob's goals are
 * stripped we keep a snapshot (priority + goal) so the exact vanilla set can be re-added if the option is
 * later turned off, without reconstructing subtype-specific goals by hand.
 *
 * <p>The snapshot is held ONLY while a mob is stripped and is keyed by the (post-spawn, stable) entity id;
 * it is dropped on entity unload ({@code EntityEventsInit}), so a stripped mob's goals — which strongly
 * reference the mob — never pin it in memory past despawn/death.
 */
public final class VanillaTargetingGoals {
    private VanillaTargetingGoals() {}

    /** entityId -> the vanilla target goals removed from it (present iff currently stripped). */
    private static final Map<Integer, List<WrappedGoal>> STRIPPED = new ConcurrentHashMap<>();

    /** Remove every target goal, remembering the set for a later {@link #restore}. No-op if already stripped. */
    public static void strip(Mob mob) {
        GoalSelector ts = ((MobGoalsAccessor) mob).lethalbreed$targetSelector();
        // computeIfAbsent guards double-strip: the snapshot is only ever taken while goals are still present.
        STRIPPED.computeIfAbsent(mob.getId(), id -> {
            List<WrappedGoal> saved = new ArrayList<>(ts.getAvailableGoals());
            ts.removeAllGoals(g -> true);
            return saved;
        });
    }

    /** Re-add the exact vanilla target goals captured by {@link #strip}. No-op if the mob isn't stripped. */
    public static void restore(Mob mob) {
        List<WrappedGoal> saved = STRIPPED.remove(mob.getId());
        if (saved == null) {
            return;
        }
        GoalSelector ts = ((MobGoalsAccessor) mob).lethalbreed$targetSelector();
        for (WrappedGoal w : saved) {
            ts.addGoal(w.getPriority(), w.getGoal());
        }
    }

    /** Drop any snapshot for this entity id (call on unload so dead/unloaded mobs aren't pinned). */
    public static void drop(int entityId) {
        STRIPPED.remove(entityId);
    }
}
