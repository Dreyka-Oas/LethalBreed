package oas.dreyka.lethalbreed.mixin.client.model;

import oas.dreyka.lethalbreed.client.ArmPose;
import oas.dreyka.lethalbreed.client.ZombieRenderFlags;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.zombie.AbstractZombieModel;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops a day-sleeping zombie's arms to rest along its body instead of the vanilla raised-forward pose.
 * Runs at TAIL of the shared-model {@code setupAnim}, AFTER vanilla has set the pose, and only writes the
 * arm rotations when the render state says this zombie is asleep. Non-sleeping frames keep vanilla's pose
 * (the parts are shared across all zombies, so we must not mutate them otherwise). Mirrors the belly hook
 * ({@code ZombieBellyModelMixin}).
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractZombieModel.class)
public class ZombieSleepArmsMixin {

    // require = 0 because this injection is cosmetic. lethalbreed.mixins.json sets defaultRequire = 1,
    // which turns a failed injection into a crash at load: right for a gameplay mixin, wrong here. A HUD or
    // render mod injecting into the same target should cost a visual effect, not the whole game.
    @Inject(require = 0, method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)V",
            at = @At("TAIL"))
    private void lethalbreed$sleepArms(ZombieRenderState state, CallbackInfo ci) {
        if (!((ZombieRenderFlags) state).lethalbreed$sleeping()) {
            return;
        }
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        ArmPose.set(model.rightArm, 0.0f, 0.0f, 0.0f);
        ArmPose.set(model.leftArm, 0.0f, 0.0f, 0.0f);
    }
}
