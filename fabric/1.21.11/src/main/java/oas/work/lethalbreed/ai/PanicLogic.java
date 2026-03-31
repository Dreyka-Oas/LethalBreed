package oas.work.lethalbreed.ai;

import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import oas.work.lethalbreed.config.ModConfig;

public class PanicLogic {
    public static void alertAllies(ZombieEntity zombie) {
        zombie.playSound(net.minecraft.sound.SoundEvents.ENTITY_ZOMBIE_HURT, 3.0f, 0.5f);
        var world = (ServerWorld) zombie.getEntityWorld();
        var allies = world.getEntitiesByClass(ZombieEntity.class, 
            zombie.getBoundingBox().expand(ModConfig.INSTANCE.panic.allyAlertRange), 
            e -> e != zombie && e.isAlive());
        Vec3d pos = new Vec3d(zombie.getX(), zombie.getY(), zombie.getZ());
        for (ZombieEntity ally : allies) {
            HearingRegistry.register(ally.getId(), pos);
        }
    }

    public static int getPackSize(ZombieEntity zombie) {
        return zombie.getEntityWorld().getEntitiesByClass(ZombieEntity.class, 
            zombie.getBoundingBox().expand(10), e -> e != zombie).size();
    }
}
