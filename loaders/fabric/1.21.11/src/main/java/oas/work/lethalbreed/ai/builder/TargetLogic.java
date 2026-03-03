/**
 * Project: Lethal Breed
 * Responsibility: AI Target and Sound Logic
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.ai.builder;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.Vec3d;
import oas.work.lethalbreed.config.ModConfig;
import oas.work.lethalbreed.ai.HearingRegistry;

public class TargetLogic {
    private Vec3d soundPos = null;
    private int soundTicks = 0;

    public Vec3d getTarget(ZombieEntity z, LivingEntity t) {
        if (t != null && z.canSee(t)) {
            soundPos = null; soundTicks = 0;
            HearingRegistry.clear(z.getId());
            return new Vec3d(t.getX(), t.getY(), t.getZ());
        }
        if (soundPos != null) {
            if (--soundTicks <= 0) soundPos = null;
            return soundPos;
        }
        Vec3d newSound = HearingRegistry.get(z.getId());
        if (newSound != null) {
            soundPos = newSound;
            soundTicks = ModConfig.INSTANCE.ai.soundLockTicks;
            HearingRegistry.clear(z.getId());
            return soundPos;
        }
        return (t != null ? new Vec3d(t.getX(), t.getY(), t.getZ()) : null);
    }

    public void reset() { soundPos = null; soundTicks = 0; }
    public boolean hasSound() { return soundPos != null || soundTicks > 0; }
}
