/**
 * Project: Lethal Breed
 * Responsibility: Vertical Movement and Pillar Placement
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.ai.builder;

import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import oas.work.lethalbreed.mixin.EntityAccessor;

public class JumpAction {
    public static int tick(ZombieEntity z, BlockPos base, int timer) {
        z.getJumpControl().setActive();
        z.setVelocity(0, z.getVelocity().y, 0);
        z.velocityDirty = true;
        
        double dy = z.getY() - base.getY();

        // VERY flexible placement condition to guarantee the pillar
        // > 0.75: We are high enough that the block pushes us upward
        // > 0.5 + Fall: We missed the peak but are still above, place to catch ourselves
        if (dy > 0.75 || (dy > 0.5 && z.getVelocity().y < 0)) {
            BlockSetter.placeDirt(((EntityAccessor)z).getWorld(), base);
            return 0; // Finished
        }
        
        // Reset if stuck on the ground for too long (1.0s)
        if (z.isOnGround() && timer > 20) return 0;
        
        return 1; // In progress
    }
}
