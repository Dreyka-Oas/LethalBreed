package oas.work.lethalbreed.mixin;

import net.minecraft.entity.mob.ZombieEntity;
import oas.work.lethalbreed.ai.builder.ZombieBuildGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombieEntity.class)
public abstract class ZombieGoalMixin {
    @Inject(method = "initGoals", at = @At("HEAD"))
    private void addCustomGoals(CallbackInfo ci) {
        ZombieEntity zombie = (ZombieEntity) (Object) this;
        MobEntityAccessor accessor = (MobEntityAccessor) zombie;
        
        accessor.getTargetSelector().clear(g -> true);

        accessor.getGoalSelector().add(0, new oas.work.lethalbreed.ai.FleeExplosionGoal(zombie));
        accessor.getGoalSelector().add(1, new oas.work.lethalbreed.ai.KamikazeGoal(zombie));
        accessor.getGoalSelector().add(2, new oas.work.lethalbreed.ai.ZombiePanicGoal(zombie));
        accessor.getGoalSelector().add(3, new ZombieBuildGoal(zombie));

        accessor.getTargetSelector().add(0, new oas.work.lethalbreed.ai.ClosestVisibleTargetGoal(zombie));
    }
}





