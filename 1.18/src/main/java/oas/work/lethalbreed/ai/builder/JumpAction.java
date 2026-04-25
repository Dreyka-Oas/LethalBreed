package oas.work.lethalbreed.ai.builder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Zombie;
import oas.work.lethalbreed.mixin.EntityAccessor;

public class JumpAction {
    public static int tick(Zombie z, BlockPos base, int timer) {
        z.getJumpControl().jump();
        z.setDeltaMovement(0, z.getDeltaMovement().y, 0);
        z.hasImpulse = true;
        
        double dy = z.getY() - base.getY();

        if (dy > 0.75 || (dy > 0.5 && z.getDeltaMovement().y < 0)) {
            BlockSetter.placeDirt(((EntityAccessor)z).getWorld(), base);
            return 0;
        }
        
        if (z.isOnGround() && timer > 20) return 0;
        
        return 1;
    }
}







