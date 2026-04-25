package oas.work.lethalbreed.ai;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import oas.work.lethalbreed.config.ModConfig;

public class HearingLogic {
    public static void reloadConfig() {}

    public static void onGameEvent(ServerLevel world, Holder<GameEvent> event, Vec3 pos, Entity source) {
        if (source instanceof Monster) return;

        if (event.value() == GameEvent.STEP.value() || event.value() == GameEvent.BLOCK_PLACE.value() ||
            event.value() == GameEvent.BLOCK_DESTROY.value() || event.value() == GameEvent.EAT.value() ||
            event.value() == GameEvent.HIT_GROUND.value() || event.value() == GameEvent.PROJECTILE_LAND.value() ||
            event.value() == GameEvent.EXPLODE.value() || event.value() == GameEvent.SPLASH.value() ||
            event.value() == GameEvent.ENTITY_PLACE.value()) {
            
            double range = ModConfig.INSTANCE.ai.hearingRange;
            var zombies = world.getEntitiesOfClass(Zombie.class, 
                new AABB(pos.x-range, pos.y-range, pos.z-range, pos.x+range, pos.y+range, pos.z+range), z -> z.isAlive());
            
            for (Zombie z : zombies) {
                if (z == source) continue;
                HearingRegistry.register(z.getId(), pos);
            }
        }
    }
}







