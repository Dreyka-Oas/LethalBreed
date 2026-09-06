package oas.dreyka.lethalbreed.mixin.client.model;

import oas.dreyka.lethalbreed.client.LimbPose;
import oas.dreyka.lethalbreed.client.ZombieRenderFlags;
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

    // require = 0 because this injection is cosmetic. lethalbreed.mixins.json sets defaultRequire = 1,
    // which turns a failed injection into a crash at load: right for a gameplay mixin, wrong here. A HUD or
    // render mod injecting into the same target should cost a visual effect, not the whole game.
    @Inject(require = 0, method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void lethalbreed$zombieArms(AvatarRenderState state, CallbackInfo ci) {
        if (!((ZombieRenderFlags) state).lethalbreed$hallucinateZombie()) {
            return;
        }
        // Classic zombie stance: both arms straight out (~90° forward), no swing, slight inward tilt.
        LimbPose.set(this.rightArm, -1.5f, 0.0f, 0.05f);
        LimbPose.set(this.leftArm, -1.5f, 0.0f, -0.05f);
    }
}
