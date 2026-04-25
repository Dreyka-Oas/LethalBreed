package oas.work.lethalbreed.ai;

import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import oas.work.lethalbreed.config.ModConfig;
import java.util.List;

public class PanicLogic {
    public static void reloadConfig() {}

    public static void alertAllies(Zombie zombie) {
        zombie.playSound(SoundEvents.ZOMBIE_HURT, 3.0f, 0.5f);
        double range = ModConfig.INSTANCE.panic.allyAlertRange;
        List<Zombie> allies = zombie.level().getEntitiesOfClass(
            Zombie.class, zombie.getBoundingBox().inflate(range),
            z -> true
        );
        Vec3 pos = zombie.position();
        for (Zombie ally : allies) {
            if (ally != zombie && ally.isAlive()) {
                HearingRegistry.register(ally.getId(), pos);
            }
        }
    }

    public static int getPackSize(Zombie zombie) {
        List<Zombie> list = zombie.level().getEntitiesOfClass(
            Zombie.class, zombie.getBoundingBox().inflate(10),
            z -> true
        );
        int count = 0;
        for (Zombie z : list) {
            if (z != zombie) count++;
        }
        return count;
    }
}