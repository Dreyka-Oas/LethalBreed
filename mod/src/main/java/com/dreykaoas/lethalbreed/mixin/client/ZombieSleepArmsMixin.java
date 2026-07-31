package com.dreykaoas.lethalbreed.mixin.client;

import com.dreykaoas.lethalbreed.client.BellyChargeHolder;
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

    // require = 0: purely presentational, same reasoning as ZombieBellyModelMixin on this very method.
    // lethalbreed.mixins.json sets defaultRequire=1, which turns any failed injection into a hard crash at
    // load — correct for gameplay mixins, wrong here. A HUD or render mod that redirects the same call
    // should cost the player a sleep pose, not the game.
    @Inject(require = 0, method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)V",
            at = @At("TAIL"))
    private void lethalbreed$sleepArms(ZombieRenderState state, CallbackInfo ci) {
        if (!((BellyChargeHolder) state).lethalbreed$sleeping()) {
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
