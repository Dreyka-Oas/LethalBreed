package oas.work.lethalbreed;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.TickEvent;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import oas.work.lethalbreed.ai.builder.ObstructionAnalyzer;
import oas.work.lethalbreed.ai.TemporaryBlockTracker;

public class LethalBreedEvents {
    private static long tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tickCounter++;
            ObstructionAnalyzer.onTick(tickCounter);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel serverLevel) {
            TemporaryBlockTracker.onTick(serverLevel);
            
            double minX = serverLevel.getWorldBorder().getMinX();
            double minZ = serverLevel.getWorldBorder().getMinZ();
            double maxX = serverLevel.getWorldBorder().getMaxX();
            double maxZ = serverLevel.getWorldBorder().getMaxZ();
            AABB bounds = new AABB(minX, serverLevel.getMinBuildHeight(), minZ, maxX, serverLevel.getMaxBuildHeight(), maxZ);
            
            for (Zombie zombie : serverLevel.getEntitiesOfClass(Zombie.class, bounds, z -> z.isAlive() && z.getTags().contains("lethal_mutant"))) {
                MutantLogic.onServerTick(zombie);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Zombie zombie) {
            Level level = event.getLevel();
            if (level != null && !level.isClientSide()) {
                SizeLogic.randomizeStats(zombie);
                EquipmentLogic.randomizeEquipment(zombie);
            }
        }
    }
}
