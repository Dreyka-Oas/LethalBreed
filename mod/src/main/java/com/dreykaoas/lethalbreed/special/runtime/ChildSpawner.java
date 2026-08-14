package com.dreykaoas.lethalbreed.special.runtime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Spawns a child zombie next to a parent (necromancer summon, splitter death). Picks a random XZ offset within
 * {@code spread} blocks and, if that attempt yields nothing usable, retries — falling back to the parent's own
 * position — so a summon in a tight space never silently produces nothing. Returns the spawned child, or
 * {@code null} if every attempt was lost.
 */
final class ChildSpawner {
    private ChildSpawner() {
    }

    /** Attempts before giving up. Past this, a run of discards has stopped being plausible and the loop must
     *  not become the thing that hangs a tick. */
    private static final int ATTEMPTS = 5;

    static Zombie spawnNear(ServerLevel level, Zombie parent, int spread) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            // Last attempt falls back to the parent's own cell, which is known to admit an entity.
            boolean fallback = attempt == ATTEMPTS - 1;
            int dx = fallback ? 0 : level.getRandom().nextInt(spread * 2 + 1) - spread;
            int dz = fallback ? 0 : level.getRandom().nextInt(spread * 2 + 1) - spread;
            Zombie child = EntityType.ZOMBIE.spawn(level, parent.blockPosition().offset(dx, 0, dz),
                    EntitySpawnReason.MOB_SUMMONED);
            // Null is not the only failure. Vanilla makes ~5 % of zombies babies, and the mod's
            // blockBabyZombies rule discards those at ENTITY_LOAD — which fires INSIDE spawn(). The call then
            // hands back a non-null entity that is already removed, so a summoner silently loses that child
            // and a Splitter can stop splitting altogether. The dev harness has worked around this for a
            // while (ArenaBuilder.spawnZombie); production never did.
            if (child != null && !child.isRemoved()) {
                return child;
            }
            if (child != null) {
                child.discard(); // already gone, but never leave a half-added entity behind
            }
        }
        return null;
    }
}
