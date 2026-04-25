package oas.work.lethalbreed.ai;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import oas.work.lethalbreed.config.ModConfig;

public class PanicLogic {
    public static void reloadConfig() {}

    public static void alertAllies(Zombie zombie) {
        zombie.playSound(net.minecraft.sounds.SoundEvents.ZOMBIE_HURT, 3.0f, 0.5f);
        var world = (ServerLevel) zombie.level();
        var allies = world.getEntitiesOfClass(Zombie.class, 
            zombie.getBoundingBox().inflate(ModConfig.INSTANCE.panic.allyAlertRange), 
            e -> e != zombie && e.isAlive());
        Vec3 pos = new Vec3(zombie.getX(), zombie.getY(), zombie.getZ());
        for (Zombie ally : allies) {
            HearingRegistry.register(ally.getId(), pos);
        }
    }

    public static int getPackSize(Zombie zombie) {
        return zombie.level().getEntitiesOfClass(Zombie.class, 
            zombie.getBoundingBox().inflate(10), e -> e != zombie).size();
    }
}









