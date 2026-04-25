package oas.work.lethalbreed.ai;

import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.core.Holder;
import oas.work.lethalbreed.config.ModConfig;

public class HearingLogic {
    public static void reloadConfig() {}

    public static void onGameEvent(Level world, Holder<GameEvent> event, Vec3 pos, Entity source) {
        if (source instanceof Zombie) return;

        if (event.is(GameEvent.STEP) || event.is(GameEvent.BLOCK_PLACE) || event.is(GameEvent.BLOCK_DESTROY) ||
            event.is(GameEvent.EAT) || event.is(GameEvent.HIT_GROUND) || event.is(GameEvent.PROJECTILE_LAND) ||
            event.is(GameEvent.EXPLODE) || event.is(GameEvent.SPLASH) || event.is(GameEvent.ENTITY_PLACE) ||
            event.is(GameEvent.TELEPORT)) {

            double range = ModConfig.INSTANCE.ai.hearingRange;
            var zombies = world.getEntitiesOfClass(
                Zombie.class,
                new net.minecraft.world.phys.AABB(pos.x - range, pos.y - range, pos.z - range, pos.x + range, pos.y + range, pos.z + range),
                z -> z.isAlive()
            );

            for (Zombie z : zombies) {
                if (z == source) continue;
                HearingRegistry.register(z.getId(), pos);
            }
        }
    }
}
