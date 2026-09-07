package oas.dreyka.lethalbreed.entity.spawn;

import oas.dreyka.lethalbreed.api.event.SpawnCullCallback;
import oas.dreyka.lethalbreed.config.domain.WorldSpawnConfig;
import oas.dreyka.lethalbreed.phase.PhaseManager;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Phase-gated hostile spawn filter. The mod owns the entire hostile population:
 *
 * <ul>
 *   <li>Phase 0 ("classic"): no hostile mob spawns at all.</li>
 *   <li>Phases 1..15: when {@link WorldSpawnConfig#onlyPlainZombie} is ON, only a plain {@link Zombie}
 *       (NOT Husk / ZombieVillager / ZombifiedPiglin / Drowned, which are all subclasses) survives; every
 *       other {@link MobCategory#MONSTER} is culled.</li>
 * </ul>
 *
 * <p>That rule takes the whole hostile population with it, including whatever another mod adds. Two ways
 * out, both of them another author's to use and neither of them a config option a player can turn on: the
 * {@link #SPAWN_PROTECTED} tag for a fixed list of entity types, and {@link SpawnCullCallback} for a
 * decision that has to look at the mob.
 *
 * <p>Passive/ambient/water mobs are never touched. The {@code ENTITY_LOAD} hook fires identically for a
 * fresh spawn and for a chunk simply coming back with the same mob in it, so the hook calls
 * {@link #shouldCullOnLoad} and not {@link #shouldCull}: the first of the two remembers the UUID and
 * therefore never answers true twice for the same mob, the second is a plain read of the current rules that
 * the dev harnesses can re-ask as often as they need.
 */
public final class SpawnFilter {
    private SpawnFilter() {}

    /** UUIDs already handed to {@link #shouldCullOnLoad} this session, standing in for the "first add" flag
     *  {@code Entity} lacks. Hostiles only, emptied entry by entry as they die ({@link #onEntityUnload})
     *  and wholesale at SERVER_STOPPED ({@link #onServerStopped}). */
    private static final Set<UUID> LOADED_ONCE = new HashSet<>();

    /** Drops the seen-UUID set. Static state outlives the world, so a set left standing would carry a
     *  singleplayer session's verdicts into the next world opened in the same JVM, where a dedicated server
     *  restarting the process would start over. */
    public static void onServerStopped() {
        LOADED_ONCE.clear();
    }

    /** ENTITY_UNLOAD: a mob that will never load again leaves the set. Without this the set only grows, one
     *  entry per hostile the world has ever loaded, and a server up for days holds every mob it has killed.
     *  Only KILLED and DISCARDED go: a chunk unload (null reason, or UNLOADED_*) and a dimension change both
     *  come back under the same UUID, and forgetting those would re-open the reload cull this class exists
     *  to close. */
    public static void onEntityUnload(Entity entity) {
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
            LOADED_ONCE.remove(entity.getUUID());
        }
    }

    /** Records the UUID and answers whether this add is its first this session. */
    static boolean firstLoadThisSession(UUID id) {
        return LOADED_ONCE.add(id);
    }

    /** The ENTITY_LOAD verdict: cull only on the entity's first add, so a mob that already survived its
     *  spawn is not discarded later just because its chunk came back or the phase moved under it. */
    public static boolean shouldCullOnLoad(Entity entity) {
        if (!(entity instanceof Mob mob) || mob.getType().getCategory() != MobCategory.MONSTER) {
            return false; // only hostile mobs are governed here, so only they go into LOADED_ONCE
        }
        return firstLoadThisSession(entity.getUUID()) && shouldCull(entity);
    }

    /** The two vanilla bosses. Both are MobCategory.MONSTER, so the phase gate below would discard them like
     *  any hostile, and a summoned Wither or a live Dragon would vanish the next time its chunk loads. */
    public static boolean isProtectedBoss(EntityType<?> type) {
        return type == EntityType.ENDER_DRAGON || type == EntityType.WITHER;
    }

    /** Entity types a datapack, or another mod's own data, declares off limits. Empty in the shipped jar:
     *  it exists so that sparing a boss costs a JSON file rather than a listener. */
    public static final TagKey<EntityType<?>> SPAWN_PROTECTED = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath("lethalbreed", "spawn_protected"));

    /** True if this entity must be discarded at load under the current phase + filter config. Reads only:
     *  the dev harnesses re-ask it on a prop to explain a failure, and must get the same answer twice.
     *
     *  <p>The listeners are asked last, and only about a mob the rules have already condemned. Asking them
     *  about every hostile that was going to live anyway would put another mod's code on the load path of
     *  the one mob population this mod exists to own. */
    public static boolean shouldCull(Entity entity) {
        return culledByRules(entity)
                && SpawnCullCallback.EVENT.invoker().allowCull(entity, PhaseManager.current(), true);
    }

    private static boolean culledByRules(Entity entity) {
        if (!(entity instanceof Mob mob) || mob.getType().getCategory() != MobCategory.MONSTER) {
            return false; // only hostile mobs are governed here
        }
        if (isProtectedBoss(mob.getType()) || mob.getType().is(SPAWN_PROTECTED)) {
            return false;
        }
        // Phase 0 = classic: nothing hostile spawns.
        if (PhaseManager.current() <= 0) {
            return true;
        }
        // Phases 1..15: keep only the exact plain-Zombie class when the filter is on.
        if (WorldSpawnConfig.onlyPlainZombie) {
            return entity.getClass() != Zombie.class;
        }
        return false;
    }
}
