package oas.dreyka.lethalbreed.mixin.client.model;

import oas.dreyka.lethalbreed.client.ClingPose;
import oas.dreyka.lethalbreed.client.LimbPose;
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
 * Poses a latched zombie: arms closed around the prey, knees drawn up, head bowed into it and nodding as it
 * bites. Runs at TAIL of the shared-model {@code setupAnim}, AFTER vanilla has posed the zombie, and only
 * writes anything when the render state says this one is clinging. The parts belong to every zombie on
 * screen, so an unlatched frame must leave vanilla's pose alone. Same shape as
 * {@code ZombieSleepArmsMixin} next door; the two flags come from mutually exclusive states, so they never
 * fight over the same frame.
 *
 * <p>The rotations and the nod live in {@code oas.dreyka.lethalbreed.client.ClingPose}, which a test can
 * load; this class is the wiring.
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractZombieModel.class)
public class ZombieClingPoseMixin {

    // require = 0 because this injection is cosmetic. lethalbreed.mixins.json sets defaultRequire = 1,
    // which turns a failed injection into a crash at load: right for a gameplay mixin, wrong here. A HUD or
    // render mod injecting into the same target should cost a visual effect, not the whole game.
    @Inject(require = 0, method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)V",
            at = @At("TAIL"))
    private void lethalbreed$clingPose(ZombieRenderState state, CallbackInfo ci) {
        if (!((ZombieRenderFlags) state).lethalbreed$clinging()) {
            return;
        }
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        LimbPose.set(model.rightArm, ClingPose.ARM_X, 0.0f, ClingPose.ARM_Z);
        LimbPose.set(model.leftArm, ClingPose.ARM_X, 0.0f, -ClingPose.ARM_Z);
        LimbPose.set(model.rightLeg, ClingPose.LEG_X, 0.0f, -ClingPose.LEG_Z);
        LimbPose.set(model.leftLeg, ClingPose.LEG_X, 0.0f, ClingPose.LEG_Z);

        // The hat layer follows for free: since 1.21 it is built as a CHILD of head rather than a sibling
        // kept in step by hand, so it inherits the rotation instead of hanging where the head used to be.
        LimbPose.set(model.head, ClingPose.headPitch(state.ageInTicks), 0.0f,
                ClingPose.headRoll(state.ageInTicks));
    }
}
