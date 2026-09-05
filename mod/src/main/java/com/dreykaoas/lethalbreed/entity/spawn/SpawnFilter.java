package com.dreykaoas.lethalbreed.entity.spawn;

import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

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
 * <p>Passive/ambient/water mobs are never touched. The {@code ENTITY_LOAD} hook fires identically for a
 * fresh spawn and for a chunk simply coming back with the same mob in it, so the hook calls
 * {@link #shouldCullOnLoad} and not {@link #shouldCull}: the first of the two remembers the UUID and
 * therefore never answers true twice for the same mob, the second is a plain read of the current rules that
 * the dev harnesses can re-ask as often as they need.
 */
public final class SpawnFilter {
    private SpawnFilter() {}

    /** UUIDs already handed to {@link #shouldCullOnLoad} this session, standing in for the "first add" flag
     *  {@code Entity} lacks. Hostiles only, and dropped at SERVER_STOPPED by {@link #onServerStopped}. */
    private static final Set<UUID> loadedOnce = new HashSet<>();

    /** Drops the seen-UUID set. Static state outlives the world, so a set left standing would carry a
     *  singleplayer session's verdicts into the next world opened in the same JVM, where a dedicated server
     *  restarting the process would start over. */
    public static void onServerStopped() {
        loadedOnce.clear();
    }

    /** Records the UUID and answers whether this add is its first this session. */
    static boolean firstLoadThisSession(UUID id) {
        return loadedOnce.add(id);
    }

    /** The ENTITY_LOAD verdict: cull only on the entity's first add, so a mob that already survived its
     *  spawn is not discarded later just because its chunk came back or the phase moved under it. */
    public static boolean shouldCullOnLoad(Entity entity) {
        if (!(entity instanceof Mob mob) || mob.getType().getCategory() != MobCategory.MONSTER) {
            return false; // only hostile mobs are governed here, so only they go into loadedOnce
        }
        return firstLoadThisSession(entity.getUUID()) && shouldCull(entity);
    }

    /** The two vanilla bosses. Both are MobCategory.MONSTER, so the phase gate below would discard them like
     *  any hostile, and a summoned Wither or a live Dragon would vanish the next time its chunk loads. */
    public static boolean isProtectedBoss(EntityType<?> type) {
        return type == EntityType.ENDER_DRAGON || type == EntityType.WITHER;
    }

    /** True if this entity must be discarded at load under the current phase + filter config. Reads only:
     *  the dev harnesses re-ask it on a prop to explain a failure, and must get the same answer twice. */
    public static boolean shouldCull(Entity entity) {
        if (!(entity instanceof Mob mob) || mob.getType().getCategory() != MobCategory.MONSTER) {
            return false; // only hostile mobs are governed here
        }
        if (isProtectedBoss(mob.getType())) {
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
