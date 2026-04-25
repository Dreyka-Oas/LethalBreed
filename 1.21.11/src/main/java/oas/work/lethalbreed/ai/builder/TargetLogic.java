package oas.work.lethalbreed.ai.builder;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import oas.work.lethalbreed.ai.HearingRegistry;

public class TargetLogic {
    private Vec3 soundPos = null;
    private int soundTicks = 0, targetMemory = 0;

    public Vec3 getTarget(Zombie z, LivingEntity t) {
        if (t != null && z.getSensing().hasLineOfSight(t)) {
            soundPos = null;
            soundTicks = 0;
            targetMemory = 60;
            HearingRegistry.clear(z.getId());
            return new Vec3(t.getX(), t.getY(), t.getZ());
        }

        if (targetMemory > 0) {
            targetMemory--;
            if (t != null && t.isAlive()) return new Vec3(t.getX(), t.getY(), t.getZ());
        }

        if (soundPos != null) {
            if (--soundTicks <= 0) {
                soundPos = null;
            } else {
                return soundPos;
            }
        }

        Vec3 newSound = HearingRegistry.get(z.getId());
        if (newSound != null) {
            soundPos = newSound;
            soundTicks = oas.work.lethalbreed.config.ModConfig.INSTANCE.ai.soundLockTicks;
            HearingRegistry.clear(z.getId());
            return soundPos;
        }
        return null;
    }

    public void reset() {
        soundPos = null;
        soundTicks = 0;
    }

    public boolean hasSound(Zombie z) {
        return soundPos != null || soundTicks > 0 || HearingRegistry.get(z.getId()) != null;
    }
}