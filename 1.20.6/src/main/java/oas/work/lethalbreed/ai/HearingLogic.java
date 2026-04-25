package oas.work.lethalbreed.ai;

import java.util.List;
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

    public static void onGameEvent(ServerLevel world, GameEvent event, Vec3 pos, Entity source) {
        if (source instanceof Monster) return;

        if (event == GameEvent.STEP.value() || event == GameEvent.BLOCK_PLACE.value() ||
            event == GameEvent.BLOCK_DESTROY.value() || event == GameEvent.EAT.value() ||
            event == GameEvent.HIT_GROUND.value() || event == GameEvent.PROJECTILE_LAND.value() ||
            event == GameEvent.EXPLODE.value() || event == GameEvent.SPLASH.value() ||
            event == GameEvent.ENTITY_PLACE.value()) {
            
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







