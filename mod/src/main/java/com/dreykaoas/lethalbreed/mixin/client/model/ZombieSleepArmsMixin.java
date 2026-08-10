package com.dreykaoas.lethalbreed.mixin.client.model;

import com.dreykaoas.lethalbreed.client.ZombieRenderFlags;
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
 * arm rotations when the render state says this zombie is asleep — non-sleeping frames keep vanilla's pose
 * (the parts are shared across all zombies, so we must not mutate them otherwise). Mirrors the belly hook
 * ({@code ZombieBellyModelMixin}).
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractZombieModel.class)
public class ZombieSleepArmsMixin {

    // require = 0: purely presentational — see com.dreykaoas.lethalbreed.client.PresentationalMixinNotes.
    @Inject(require = 0, method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)V",
            at = @At("TAIL"))
    private void lethalbreed$sleepArms(ZombieRenderState state, CallbackInfo ci) {
        if (!((ZombieRenderFlags) state).lethalbreed$sleeping()) {
            return;
        }
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        model.rightArm.xRot = 0.0f;
        model.leftArm.xRot = 0.0f;
        model.rightArm.yRot = 0.0f;
        model.leftArm.yRot = 0.0f;
        model.rightArm.zRot = 0.0f;
        model.leftArm.zRot = 0.0f;
    }
}
