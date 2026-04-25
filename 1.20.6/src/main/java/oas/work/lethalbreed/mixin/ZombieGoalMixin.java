package oas.work.lethalbreed.mixin;

import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.goal.Goal;
import oas.work.lethalbreed.ai.ClosestVisibleTargetGoal;
import oas.work.lethalbreed.ai.FleeExplosionGoal;
import oas.work.lethalbreed.ai.KamikazeGoal;
import oas.work.lethalbreed.ai.ZombiePanicGoal;
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
        accessor.getGoalSelector().addGoal(0, createFleeGoal(zombie));
        accessor.getGoalSelector().addGoal(1, createKamikazeGoal(zombie));
        accessor.getGoalSelector().addGoal(2, createPanicGoal(zombie));
        accessor.getGoalSelector().addGoal(3, createBuildGoal(zombie));
        accessor.getTargetSelector().addGoal(0, createVisibleTargetGoal(zombie));
    }

    private static Goal createFleeGoal(Zombie zombie) {
        return new FleeExplosionGoal(zombie);
    }

    private static Goal createKamikazeGoal(Zombie zombie) {
        return new KamikazeGoal(zombie);
    }

    private static Goal createPanicGoal(Zombie zombie) {
        return new ZombiePanicGoal(zombie);
    }

    private static Goal createBuildGoal(Zombie zombie) {
        return new ZombieBuildGoal(zombie);
    }

    private static Goal createVisibleTargetGoal(Zombie zombie) {
        return new ClosestVisibleTargetGoal(zombie);
    }
}






