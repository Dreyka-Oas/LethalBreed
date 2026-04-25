package oas.work.lethalbreed.ai.builder;

import oas.work.lethalbreed.ai.LethalThreads;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.CompletableFuture;

public class BrainProcessor {
    public static void analyze(Zombie z, Vec3 target, AtomicReference<BlockPos> resultSlot, AtomicReference<Boolean> thinking) {
        if (z == null || target == null || thinking.get()) return;
        thinking.set(true);
        
        LethalThreads.execute(() -> {
            try {
                BlockPos res = ObstructionAnalyzer.getStuckBlock(
                    ((oas.work.lethalbreed.mixin.EntityAccessor)z).getWorld(), z, target
                );
                resultSlot.set(res);
            } catch (Exception e) {
            } finally {
                thinking.set(false);
            }
        });
    }
}









