package com.dreykaoas.lethalbreed.special.runtime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Spawns a child zombie next to a parent (necromancer summon, splitter death). Picks a random XZ offset within
 * {@code spread} blocks and, if that cell is blocked, retries on the parent's own position so a summon in a
 * tight space never silently produces nothing. Returns the spawned child, or {@code null} if even the fallback
 * could not place it.
 */
final class ChildSpawner {
    private ChildSpawner() {
    }

    static Zombie spawnNear(ServerLevel level, Zombie parent, int spread) {
        int dx = level.getRandom().nextInt(spread * 2 + 1) - spread;
        int dz = level.getRandom().nextInt(spread * 2 + 1) - spread;
        Zombie child = EntityType.ZOMBIE.spawn(level, parent.blockPosition().offset(dx, 0, dz),
                EntitySpawnReason.MOB_SUMMONED);
        if (child == null) {
            child = EntityType.ZOMBIE.spawn(level, parent.blockPosition(), EntitySpawnReason.MOB_SUMMONED);
        }
        return child;
    }
}
