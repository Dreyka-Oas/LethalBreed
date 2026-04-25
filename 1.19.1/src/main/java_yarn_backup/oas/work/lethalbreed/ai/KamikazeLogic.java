package oas.work.lethalbreed.ai;

import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.sound.SoundEvents;

public class KamikazeLogic {
    public static void prime(ZombieEntity zombie, float bonus) {
        zombie.addScoreboardTag("lethal_primed");
        zombie.playSound(SoundEvents.ENTITY_TNT_PRIMED, 2.0f, bonus);
        zombie.getNavigation().stop();
    }

    public static void tickShake(ZombieEntity zombie, float bonus) {
        double j = (zombie.getRandom().nextDouble() - 0.5) * (0.1 * bonus);
        zombie.refreshPositionAndAngles(zombie.getX() + j, zombie.getY(), zombie.getZ() + j, 
            zombie.getYaw(), zombie.getPitch());
    }
}







