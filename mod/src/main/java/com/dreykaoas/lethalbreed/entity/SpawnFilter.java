package com.dreykaoas.lethalbreed.entity;

import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.entity.gecko.HorrorZombie;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.zombie.Zombie;

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
 * <p>Passive/ambient/water mobs are never touched. Called from the {@code ENTITY_LOAD} hook.
 */
public final class SpawnFilter {
    private SpawnFilter() {}

    /** True if this entity must be discarded at load under the current phase + filter config. */
    public static boolean shouldCull(Entity entity) {
        // Our custom horror zombie is never subject to the phase-gated cull — it is a deliberately-spawned
        // boss-type entity, not part of the governed ambient zombie population.
        if (entity instanceof HorrorZombie) {
            return false;
        }
        if (!(entity instanceof Mob mob) || mob.getType().getCategory() != MobCategory.MONSTER) {
            return false; // only hostile mobs are governed here
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
