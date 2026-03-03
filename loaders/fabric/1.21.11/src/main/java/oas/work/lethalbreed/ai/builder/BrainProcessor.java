/**
 * Project: Lethal Breed
 * Responsibility: Asynchronous AI Logic Processor
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.ai.builder;

import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import oas.work.lethalbreed.ai.LethalThreads;
import oas.work.lethalbreed.mixin.EntityAccessor;
import java.util.concurrent.atomic.AtomicReference;

public class BrainProcessor {
    public static void analyze(ZombieEntity z, Vec3d target, AtomicReference<BlockPos> resultSlot, AtomicReference<Boolean> thinking) {
        if (z == null || target == null || thinking.get()) return;
        thinking.set(true);
        
        LethalThreads.execute(() -> {
            try {
                BlockPos res = ObstructionAnalyzer.getHorizontal(
                    ((oas.work.lethalbreed.mixin.EntityAccessor)z).getWorld(), z, target
                );
                resultSlot.set(res);
            } catch (Exception e) {
                // In case of error, do not block the zombie
            } finally {
                thinking.set(false);
            }
        });
    }
}