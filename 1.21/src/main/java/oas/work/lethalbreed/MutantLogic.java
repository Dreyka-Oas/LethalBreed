package oas.work.lethalbreed;
import oas.work.lethalbreed.config.ModConfig;

import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

public class MutantLogic {
    public static void makeMutant(ZombieEntity z) {
        z.addCommandTag("lethal_mutant");
    }

    public static void tickTentacles(ZombieEntity z) {
        if (!z.getCommandTags().contains("lethal_mutant") || z.age % ModConfig.INSTANCE.mutant.mutantTentacleTickRate != 0) return;
        if (!(z.getWorld() instanceof ServerWorld world)) return;

        double scale = z.getScale();
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(z.age * 10 + i * 120);
            double x = z.getX() + Math.cos(angle) * (0.2 * scale);
            double zPos = z.getZ() + Math.sin(angle) * (0.2 * scale);
            double y = z.getY() + (i * scale * 0.5);
            world.spawnParticles(ParticleTypes.SQUID_INK, x, y, zPos, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    public static void onDeath(ZombieEntity z) {
        if (!z.getCommandTags().contains("lethal_mutant")) return;
        ServerWorld world = (ServerWorld) z.getWorld();
        for (int i = 0; i < ModConfig.INSTANCE.mutant.mutantMinionCount; i++) {
            ZombieEntity minion = new ZombieEntity(world);
            minion.refreshPositionAndAngles(z.getX(), z.getY(), z.getZ(), world.random.nextFloat() * 360, 0);
            minion.removeCommandTag("lethal_mutant"); // Security fix: prevent infinite spawn loops
            SizeLogic.randomizeStats(minion, false);
            EquipmentLogic.randomizeEquipment(minion);
            world.spawnEntity(minion);
        }
    }
}





