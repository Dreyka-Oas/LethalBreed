package com.dreykaoas.lethalbreed.entity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks every vanilla zombie the mod has taken over, keyed by entity id. Backed by a concurrent
 * map so later phases can read it from worker threads while the server thread mutates it.
 */
public final class ZombieRegistry {
    private final Map<Integer, SmartZombie> byId = new ConcurrentHashMap<>();

    public SmartZombie add(Zombie zombie, ResourceKey<Level> dimension) {
        // Bucket membership is derived live from the entity id + current tickBuckets (see LodBucketPass),
        // not cached here — so changing tickBuckets at runtime re-spreads the whole population immediately
        // instead of stranding zombies on a stale bucket index.
        SmartZombie sz = new SmartZombie(zombie, dimension);
        byId.put(zombie.getId(), sz);
        return sz;
    }

    public SmartZombie remove(int id) {
        return byId.remove(id);
    }

    public SmartZombie get(int id) {
        return byId.get(id);
    }

    public Collection<SmartZombie> all() {
        return byId.values();
    }

    public int size() {
        return byId.size();
    }

    public void clear() {
        byId.clear();
    }
}
