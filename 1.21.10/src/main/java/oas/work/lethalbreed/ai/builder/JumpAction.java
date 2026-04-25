package oas.work.lethalbreed.ai.builder;

import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import oas.work.lethalbreed.mixin.EntityAccessor;

public class JumpAction {
    public static int tick(ZombieEntity z, BlockPos base, int timer) {
        z.getJumpControl().setActive();
        z.setVelocity(0, z.getVelocity().y, 0);
        z.velocityDirty = true;
        
        double dy = z.getY() - base.getY();

        if (dy > 0.75 || (dy > 0.5 && z.getVelocity().y < 0)) {
            BlockSetter.placeDirt(((EntityAccessor)z).getWorld(), base);
            return 0;
        }
        
        if (z.isOnGround() && timer > 20) return 0;
        
        return 1;
    }
}






