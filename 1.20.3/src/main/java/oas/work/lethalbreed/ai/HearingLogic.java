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
    public static void reloadConfig() {}

    public static void onGameEvent(ServerWorld world, GameEvent event, Vec3d pos, Entity source) {
        if (source instanceof HostileEntity) return;

        if (event == GameEvent.STEP || event == GameEvent.BLOCK_PLACE || 
            event == GameEvent.BLOCK_DESTROY || event == GameEvent.EAT ||
            event == GameEvent.HIT_GROUND || event == GameEvent.PROJECTILE_LAND ||
            event == GameEvent.EXPLODE || event == GameEvent.SPLASH ||
            event == GameEvent.ENTITY_PLACE || event == GameEvent.TELEPORT) {
            
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








