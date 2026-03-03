/**
 * Project: Lethal Breed
 * Responsibility: Game Event Hearing System
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.ai;

import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.world.event.GameEvent;
import net.minecraft.registry.entry.RegistryEntry;
import oas.work.lethalbreed.config.ModConfig;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public class HearingLogic {
    public static void onGameEvent(ServerWorld world, RegistryEntry<GameEvent> event, Vec3d pos, Entity source) {
        // RULE: A zombie NEVER listens to another zombie (except for screams, handled elsewhere)
        if (source instanceof ZombieEntity) return;

        if (event.matchesKey(GameEvent.STEP.registryKey()) || event.matchesKey(GameEvent.BLOCK_PLACE.registryKey()) || 
            event.matchesKey(GameEvent.BLOCK_DESTROY.registryKey()) || event.matchesKey(GameEvent.EAT.registryKey()) ||
            event.matchesKey(GameEvent.HIT_GROUND.registryKey())) {
            
            double range = ModConfig.INSTANCE.ai.hearingRange;
            var zombies = world.getEntitiesByClass(ZombieEntity.class, 
                new Box(pos.x-range, pos.y-range, pos.z-range, pos.x+range, pos.y+range, pos.z+range), z -> z.isAlive());
            
            for (ZombieEntity z : zombies) {
                // Never listen to own noise (redundant safety)
                if (z == source) continue;
                
                net.minecraft.entity.LivingEntity target = z.getTarget();
                if (target == null || !z.canSee(target)) {
                    HearingRegistry.register(z.getId(), pos);
                }
            }
        }
    }
}