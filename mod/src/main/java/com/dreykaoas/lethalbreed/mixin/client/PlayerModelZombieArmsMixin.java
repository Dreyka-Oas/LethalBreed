package com.dreykaoas.lethalbreed.mixin.client;

import com.dreykaoas.lethalbreed.client.BellyChargeHolder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plague hallucination arm pose: after the player model animates normally, if the render state is tagged as a
 * zombie hallucination, force the classic straight-out zombie arms (both arms raised ~90° forward). Runs only on
 * the symptomatic viewer's client; see {@code AvatarRendererHallucinationMixin}.
 */
@Environment(EnvType.CLIENT)
@Mixin(PlayerModel.class)
public abstract class PlayerModelZombieArmsMixin extends HumanoidModel<AvatarRenderState> {
    private PlayerModelZombieArmsMixin(net.minecraft.client.model.geom.ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void lethalbreed$zombieArms(AvatarRenderState state, CallbackInfo ci) {
        if (!((BellyChargeHolder) state).lethalbreed$hallucinateZombie()) {
            return;
        }
        // Classic zombie stance: both arms straight out (~90° forward), no swing, slight inward tilt.
        this.rightArm.xRot = -1.5f;
        this.leftArm.xRot = -1.5f;
        this.rightArm.yRot = 0.0f;
        this.leftArm.yRot = 0.0f;
        this.rightArm.zRot = 0.05f;
        this.leftArm.zRot = -0.05f;
    }
}
