/**
 * Project: Lethal Breed
 * Responsibility: Central Sound Target Registry
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.ai;

import net.minecraft.util.math.Vec3d;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HearingRegistry {
    private static final Map<Integer, Vec3d> SOUND_TARGETS = new ConcurrentHashMap<>();

    public static void register(int entityId, Vec3d pos) {
        // Update with the most recent sound
        SOUND_TARGETS.put(entityId, pos);
    }

    public static Vec3d get(int entityId) {
        return SOUND_TARGETS.get(entityId);
    }

    public static void clear(int entityId) {
        SOUND_TARGETS.remove(entityId);
    }
}