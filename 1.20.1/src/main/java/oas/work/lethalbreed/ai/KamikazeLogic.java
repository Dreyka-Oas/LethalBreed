package oas.work.lethalbreed.ai;

import net.minecraft.world.entity.monster.Zombie;

public class KamikazeLogic {
    public static void prime(Zombie zombie, float bonus) {
        zombie.addTag("lethal_primed");
    }

    public static void tickShake(Zombie zombie, float bonus) {
        double shake = 0.03 * bonus;
        zombie.setDeltaMovement(
            (zombie.getRandom().nextDouble() - 0.5) * shake,
            zombie.getDeltaMovement().y,
            (zombie.getRandom().nextDouble() - 0.5) * shake
        );
        zombie.hasImpulse = true;
    }
}
