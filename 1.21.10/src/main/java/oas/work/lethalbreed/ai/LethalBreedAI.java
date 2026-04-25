package oas.work.lethalbreed.ai;

import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import oas.work.lethalbreed.config.ModConfig;

public class LethalBreedAI {
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Zombie zombie && !event.getLevel().isClientSide()) {
            // Toujours adulte
            if (zombie.isBaby()) {
                zombie.setBaby(false);
            }

            // Attributs
            var followRange = zombie.getAttribute(Attributes.FOLLOW_RANGE);
            if (followRange != null) {
                followRange.setBaseValue(ModConfig.INSTANCE.attributes.zombieFollowRange);
            }

            // Goals
            boolean alreadyInjected = zombie.goalSelector.getAvailableGoals().stream()
                    .anyMatch(goal -> goal.getGoal() instanceof ZombieBuildGoal);
            
            if (!alreadyInjected) {
                zombie.goalSelector.addGoal(0, new FleeExplosionGoal(zombie));
                zombie.targetSelector.addGoal(1, new ClosestVisibleTargetGoal(zombie));
                zombie.goalSelector.addGoal(2, new ZombiePanicGoal(zombie));
                zombie.goalSelector.addGoal(2, new KamikazeGoal(zombie));
                zombie.goalSelector.addGoal(3, new ZombieBuildGoal(zombie));
            }
        }
    }
}
