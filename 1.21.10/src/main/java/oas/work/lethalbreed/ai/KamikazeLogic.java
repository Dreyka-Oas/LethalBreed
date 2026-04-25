package oas.work.lethalbreed.ai;

import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import oas.work.lethalbreed.config.ModConfig;

public class KamikazeLogic {
    public static void prime(Zombie zombie, float bonus) {
        zombie.addTag("lethal_primed");
        zombie.playSound(SoundEvents.TNT_PRIMED, 2.0f, bonus);
        zombie.getNavigation().stop();
    }

    public static void tickShake(Zombie zombie, float bonus) {
        double j = (zombie.getRandom().nextDouble() - 0.5) * (0.1 * bonus);
        zombie.snapTo(zombie.getX() + j, zombie.getY(), zombie.getZ() + j,
                      zombie.getYRot(), zombie.getXRot());    }
}