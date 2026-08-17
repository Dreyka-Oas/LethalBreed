package com.dreykaoas.lethalbreed.dimension;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds a {@link WorldAiContext} per dimension, created on demand the first time a zombie is seen
 * in that dimension.
 */
public final class DimensionManager {
    private final Map<ResourceKey<Level>, WorldAiContext> contexts = new ConcurrentHashMap<>();

    public WorldAiContext get(ResourceKey<Level> dimension) {
        return contexts.computeIfAbsent(dimension, k -> new WorldAiContext());
    }

    public Map<ResourceKey<Level>, WorldAiContext> contexts() {
        return contexts;
    }

    public void clear() {
        contexts.clear();
    }
}
