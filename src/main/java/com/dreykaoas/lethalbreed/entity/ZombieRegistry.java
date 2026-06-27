package com.dreykaoas.lethalbreed.entity;

import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
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
        int buckets = Math.max(1, LethalBreedConfig.tickBuckets);
        int bucket = Math.floorMod(zombie.getId(), buckets);
        SmartZombie sz = new SmartZombie(zombie, dimension, bucket);
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
