package oas.work.lethalbreed.mixin;

import net.minecraft.world.entity.monster.Zombie;
import oas.work.lethalbreed.ai.*;
import oas.work.lethalbreed.ai.ZombieBuildGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Zombie.class)
public abstract class ZombieGoalMixin {
    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void injectCustomGoals(CallbackInfo ci) {
        Zombie zombie = (Zombie) (Object) this;
        MobEntityAccessor accessor = (MobEntityAccessor) zombie;

        accessor.getGoalSelector().addGoal(0, new FleeExplosionGoal(zombie));
        accessor.getTargetSelector().addGoal(1, new ClosestVisibleTargetGoal(zombie));
        accessor.getGoalSelector().addGoal(2, new ZombiePanicGoal(zombie));
        accessor.getGoalSelector().addGoal(2, new KamikazeGoal(zombie));
        accessor.getGoalSelector().addGoal(3, new ZombieBuildGoal(zombie));
    }
}