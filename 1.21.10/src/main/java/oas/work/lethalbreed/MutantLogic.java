package oas.work.lethalbreed;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Zombie;
import oas.work.lethalbreed.config.ModConfig;

public class MutantLogic {
    public static void makeMutant(Zombie z) {
        z.addTag("lethal_mutant");
    }

    public static void tickTentacles(Zombie z) {
        if (!z.getTags().contains("lethal_mutant") || z.tickCount % ModConfig.INSTANCE.mutant.mutantTentacleTickRate != 0) return;
        if (!(z.level() instanceof ServerLevel world)) return;

        double scale = 1.0;
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(z.tickCount * 10 + i * 120);
            double x = z.getX() + Math.cos(angle) * (0.2 * scale);
            double zPos = z.getZ() + Math.sin(angle) * (0.2 * scale);
            double y = z.getY() + (i * scale * 0.5);
            world.sendParticles(ParticleTypes.SQUID_INK, x, y, zPos, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    public static void onDeath(Zombie z) {
        if (!z.getTags().contains("lethal_mutant")) return;
        ServerLevel world = (ServerLevel) z.level();
        for (int i = 0; i < ModConfig.INSTANCE.mutant.mutantMinionCount; i++) {
            Zombie minion = new Zombie(world);
            minion.snapTo(z.getX(), z.getY(), z.getZ(), world.random.nextFloat() * 360, 0.0f);
            minion.removeTag("lethal_mutant"); // Security fix: prevent infinite spawn loops
            SizeLogic.randomizeStats(minion, false);
            EquipmentLogic.randomizeEquipment(minion);
            world.addFreshEntity(minion);
        }
    }
}







