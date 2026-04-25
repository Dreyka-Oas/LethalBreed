package oas.work.lethalbreed.ai;

import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import oas.work.lethalbreed.config.ModConfig;

public class ExplosionLogic {
    public static void spawnParticles(ZombieEntity z, float bonus, int fuse) {
        if (((oas.work.lethalbreed.mixin.EntityAccessor)z).getWorld().isClient()) return;
        var world = (net.minecraft.server.world.ServerWorld)((oas.work.lethalbreed.mixin.EntityAccessor)z).getWorld();
        world.spawnParticles(ParticleTypes.FLAME, z.getX(), z.getY() + 1.5, z.getZ(), 3, 0.1, 0.1, 0.1, 0.05);
        if (bonus > 1.7f) {
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, z.getX(), z.getY() + 2.0, z.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
            if (fuse % 5 == 0) z.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 0.5f, 2.0f);
        }
    }

    public static void detonate(ZombieEntity z, float bonus) {
        if (((oas.work.lethalbreed.mixin.EntityAccessor)z).getWorld().isClient()) return;
        float power = ModConfig.INSTANCE.ai.kamikazeExplosionPower * z.getScale() * bonus;
        ((oas.work.lethalbreed.mixin.EntityAccessor)z).getWorld().createExplosion(z, z.getX(), z.getY(), z.getZ(), power, World.ExplosionSourceType.MOB);
        z.discard();
    }
}






