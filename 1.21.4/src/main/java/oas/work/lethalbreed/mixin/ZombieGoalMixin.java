package oas.work.lethalbreed.mixin;

import net.minecraft.world.entity.monster.Zombie;
import oas.work.lethalbreed.ai.builder.ZombieBuildGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Zombie.class)
public abstract class ZombieGoalMixin {
    @Inject(method = "registerGoals", at = @At("HEAD"))
    private void addCustomGoals(CallbackInfo ci) {
        Zombie zombie = (Zombie) (Object) this;
        MobEntityAccessor accessor = (MobEntityAccessor) zombie;
        
        accessor.getTargetSelector().removeAllGoals(goal -> true);

        accessor.getGoalSelector().addGoal(0, new oas.work.lethalbreed.ai.FleeExplosionGoal(zombie));
        accessor.getGoalSelector().addGoal(1, new oas.work.lethalbreed.ai.KamikazeGoal(zombie));
        accessor.getGoalSelector().addGoal(2, new oas.work.lethalbreed.ai.ZombiePanicGoal(zombie));
        accessor.getGoalSelector().addGoal(3, new ZombieBuildGoal(zombie));

        accessor.getTargetSelector().addGoal(0, new oas.work.lethalbreed.ai.ClosestVisibleTargetGoal(zombie));
    }
}






