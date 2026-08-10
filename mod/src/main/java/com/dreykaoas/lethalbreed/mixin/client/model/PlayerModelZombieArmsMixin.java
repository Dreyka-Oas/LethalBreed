package com.dreykaoas.lethalbreed.mixin.client.model;

import com.dreykaoas.lethalbreed.mixin.client.render.AvatarRendererHallucinationMixin;

import com.dreykaoas.lethalbreed.client.ZombieRenderFlags;
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

    // require = 0: purely presentational — see com.dreykaoas.lethalbreed.client.PresentationalMixinNotes.
    @Inject(require = 0, method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void lethalbreed$zombieArms(AvatarRenderState state, CallbackInfo ci) {
        if (!((ZombieRenderFlags) state).lethalbreed$hallucinateZombie()) {
            return;
        }
        // Classic zombie stance: both arms straight out (~90° forward), no swing, slight inward tilt.
        // Fully qualified: HumanoidModel declares its own nested ArmPose enum, which this mixin inherits
        // and which would otherwise shadow the imported com.dreykaoas.lethalbreed.client.ArmPose helper.
        com.dreykaoas.lethalbreed.client.ArmPose.set(this.rightArm, -1.5f, 0.0f, 0.05f);
        com.dreykaoas.lethalbreed.client.ArmPose.set(this.leftArm, -1.5f, 0.0f, -0.05f);
    }
}
