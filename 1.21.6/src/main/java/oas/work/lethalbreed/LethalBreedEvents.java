package oas.work.lethalbreed;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import oas.work.lethalbreed.ai.TemporaryBlockTracker;
import oas.work.lethalbreed.ai.HearingRegistry;
import oas.work.lethalbreed.ai.builder.ObstructionAnalyzer;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;

public class LethalBreedEvents {
    private static long tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        ObstructionAnalyzer.onTick(tickCounter);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            TemporaryBlockTracker.onTick(serverLevel);
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

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Zombie zombie && !zombie.level().isClientSide()) {
            if (zombie.getTags().contains("lethal_mutant")) {
                MutantLogic.onServerTick(zombie);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Zombie zombie) {
            if (!zombie.level().isClientSide()) {
                MutantLogic.onDeath(zombie);
                HearingRegistry.clear(zombie.getId());
            }
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Zombie zombie) {
            if (!zombie.level().isClientSide()) {
                HearingRegistry.clear(zombie.getId());
            }
        }
    }
}
