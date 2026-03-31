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

import net.minecraft.entity.mob.HostileEntity;

public class HearingLogic {
    public static void onGameEvent(ServerWorld world, RegistryEntry<GameEvent> event, Vec3d pos, Entity source) {
        if (source instanceof HostileEntity) return;

        if (event.matchesKey(GameEvent.STEP.registryKey()) || event.matchesKey(GameEvent.BLOCK_PLACE.registryKey()) || 
            event.matchesKey(GameEvent.BLOCK_DESTROY.registryKey()) || event.matchesKey(GameEvent.EAT.registryKey()) ||
            event.matchesKey(GameEvent.HIT_GROUND.registryKey()) || event.matchesKey(GameEvent.PROJECTILE_LAND.registryKey()) ||
            event.matchesKey(GameEvent.EXPLODE.registryKey()) || event.matchesKey(GameEvent.SPLASH.registryKey()) ||
            event.matchesKey(GameEvent.ENTITY_PLACE.registryKey()) || event.matchesKey(GameEvent.TELEPORT.registryKey())) {
            
            double range = ModConfig.INSTANCE.ai.hearingRange;
            var zombies = world.getEntitiesByClass(ZombieEntity.class, 
                new Box(pos.x-range, pos.y-range, pos.z-range, pos.x+range, pos.y+range, pos.z+range), z -> z.isAlive());
            
            for (ZombieEntity z : zombies) {
                if (z == source) continue;
                HearingRegistry.register(z.getId(), pos);
            }
        }
    }
}