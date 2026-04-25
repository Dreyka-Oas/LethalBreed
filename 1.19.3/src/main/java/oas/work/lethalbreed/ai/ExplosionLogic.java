package oas.work.lethalbreed.ai;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import oas.work.lethalbreed.config.ModConfig;

public class ExplosionLogic {
    public static void spawnParticles(Zombie z, float bonus, int fuse) {
        if (z.getLevel().isClientSide()) return;
        var world = (net.minecraft.server.level.ServerLevel)z.getLevel();
        world.sendParticles(ParticleTypes.FLAME, z.getX(), z.getY() + 1.5, z.getZ(), 3, 0.1, 0.1, 0.1, 0.05);
        if (bonus > 1.7f) {
            world.sendParticles(ParticleTypes.ELECTRIC_SPARK, z.getX(), z.getY() + 2.0, z.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
            if (fuse % 5 == 0) z.playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 0.5f, 2.0f);
        }
    }

    public static void detonate(Zombie z, float bonus) {
        if (z.getLevel().isClientSide()) return;
        float power = ModConfig.INSTANCE.ai.kamikazeExplosionPower * 1.0f * bonus;
        z.getLevel().explode(z, z.getX(), z.getY(), z.getZ(), power, Level.ExplosionInteraction.MOB);
        z.discard();
    }
}







