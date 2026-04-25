package oas.work.lethalbreed.ai;

import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.core.RegistryAccess;
import oas.work.lethalbreed.config.ModConfig;

public class HearingLogic {
    public static void reloadConfig() {}

    public static void onGameEvent(Level world, GameEvent event, Vec3 pos, Entity source) {
        if (source instanceof Zombie) return;

        if (event == GameEvent.STEP || event == GameEvent.BLOCK_PLACE || event == GameEvent.BLOCK_DESTROY ||
            event == GameEvent.EAT || event == GameEvent.HIT_GROUND || event == GameEvent.PROJECTILE_LAND ||
            event == GameEvent.EXPLODE || event == GameEvent.SPLASH || event == GameEvent.ENTITY_PLACE ||
            event == GameEvent.TELEPORT) {

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