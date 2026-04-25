package oas.work.lethalbreed.mixin;

import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.Mob;
import oas.work.lethalbreed.ai.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Zombie.class)
public abstract class ZombieGoalMixin {
    @Shadow public abstract GoalSelector getGoalSelector();
    @Shadow public abstract GoalSelector getTargetSelector();

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void injectCustomGoals(CallbackInfo ci) {
        Zombie zombie = (Zombie) (Object) this;

        this.getGoalSelector().addGoal(0, new FleeExplosionGoal(zombie));
        this.getTargetSelector().addGoal(1, new ClosestVisibleTargetGoal(zombie));
        this.getGoalSelector().addGoal(2, new ZombiePanicGoal(zombie));
        this.getGoalSelector().addGoal(2, new KamikazeGoal(zombie));
        this.getGoalSelector().addGoal(3, new ZombieBuildGoal(zombie));
    }
}
