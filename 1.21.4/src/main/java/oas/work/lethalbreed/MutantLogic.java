package oas.work.lethalbreed;

import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.EntitySpawnReason;
import oas.work.lethalbreed.config.ModConfig;
import oas.work.lethalbreed.ModLogger;

public class MutantLogic {
    public static void makeMutant(Zombie z) {
        z.addTag("lethal_mutant");
        ModLogger.info("Zombie marked as mutant!");
    }

    public static void onServerTick(Zombie z) {
        if (!z.getTags().contains("lethal_mutant")) {
            return;
        }
        Level level = z.level();
        if (level == null || level.isClientSide()) {
            return;
        }
        if (level instanceof ServerLevel world) {
            if (z.tickCount % ModConfig.INSTANCE.mutant.mutantTentacleTickRate == 0) {
                
            }
        }
    }

    public static void onDeath(Zombie z) {
        if (!z.getTags().contains("lethal_mutant")) {
            return;
        }
        Level level = z.level();
        if (!(level instanceof ServerLevel world)) {
            return;
        }
        for (int i = 0; i < ModConfig.INSTANCE.mutant.mutantMinionCount; i++) {
            Zombie minion = net.minecraft.world.entity.EntityType.ZOMBIE.create(world, EntitySpawnReason.MOB_SUMMONED);
            if (minion != null) {
                minion.moveTo(z.getX(), z.getY(), z.getZ(), world.getRandom().nextFloat() * 360, 0);
                minion.removeTag("lethal_mutant");
                SizeLogic.randomizeStats(minion);
                EquipmentLogic.randomizeEquipment(minion);
                world.addFreshEntity(minion);
            }
        }
    }
}
