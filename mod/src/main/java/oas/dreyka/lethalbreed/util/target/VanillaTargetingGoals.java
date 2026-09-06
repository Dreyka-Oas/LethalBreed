package oas.dreyka.lethalbreed.util.target;

import oas.dreyka.lethalbreed.LethalBreed;
import oas.dreyka.lethalbreed.api.LethalBreedApi;
import oas.dreyka.lethalbreed.mixin.MobGoalsAccessor;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime strip/restore of a mob's vanilla target-selection goals so {@code forceNearestTarget} can be
 * toggled LIVE on already-spawned zombies, not only on freshly spawned ones. When a mob's goals are
 * stripped we keep a snapshot (priority + goal) so the exact vanilla set can be re-added if the option is
 * later turned off, without reconstructing subtype-specific goals by hand.
 *
 * <p>The snapshot is held ONLY while a mob is stripped and is keyed by the (post-spawn, stable) entity id;
 * it is dropped on entity unload ({@code EntityEventsInit}), so a stripped mob's goals, which strongly
 * reference the mob, never pin it in memory past despawn/death.
 */
public final class VanillaTargetingGoals {
    private VanillaTargetingGoals() {}

    /** entityId -> the vanilla target goals removed from it (present iff currently stripped). */
    private static final Map<Integer, List<WrappedGoal>> STRIPPED = new ConcurrentHashMap<>();

    /** Goal classes already reported by {@link #warnOnce}, so one addon does not fill the log. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** Remove every target goal, remembering the set for a later {@link #restore}. No-op if already stripped. */
    public static void strip(Mob mob) {
        GoalSelector ts = ((MobGoalsAccessor) mob).lethalbreed$targetSelector();
        // computeIfAbsent guards double-strip: the snapshot is only ever taken while goals are still present.
        STRIPPED.computeIfAbsent(mob.getId(), id -> {
            List<WrappedGoal> saved = new ArrayList<>(ts.getAvailableGoals());
            for (WrappedGoal w : saved) {
                String cls = w.getGoal().getClass().getName();
                if (!LethalBreedApi.isAllowedAiNamespace(cls)) {
                    warnOnce(cls);
                }
            }
            ts.removeAllGoals(g -> true);
            return saved;
        });
    }

    /** AiConflictDetector only ever walks the goalSelector, so a foreign goal sitting in the targetSelector
     *  was emptied here without anyone being told. Once per class, not once per zombie. */
    private static void warnOnce(String cls) {
        if (WARNED.add(cls)) {
            LethalBreed.LOGGER.warn("[LethalBreed] removed foreign targeting goal {}: forceNearestTarget=true empties "
                    + "the whole target selector. Set forceNearestTarget=false in config/oas/lethalbreed.json to keep it. "
                    + "Calling LethalBreedApi.allowAiNamespace silences this line and the conflict scan, it does not "
                    + "keep the goal.", cls);
        }
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

    /** Empty the whole table at server stop. The per-entity {@link #drop} is the ordinary path and it is
     *  enough as long as every stripped mob is unloaded before shutdown, which is one unload event away from
     *  not being true. A single missed entry keeps a WrappedGoal, therefore its Mob, therefore the closed
     *  world's ServerLevel, alive in a static map for the rest of the process. Restoring the goals here would
     *  be pointless: the mobs are on their way out and their NBT is already written. */
    public static void clearAll() {
        STRIPPED.clear();
    }
}
