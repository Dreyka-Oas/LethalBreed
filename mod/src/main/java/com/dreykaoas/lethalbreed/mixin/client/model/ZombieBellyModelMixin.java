package com.dreykaoas.lethalbreed.mixin.client.model;

import com.dreykaoas.lethalbreed.client.ZombieRenderFlags;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.zombie.AbstractZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inflates the BOMBER's belly (the {@code body} part) as its fuse burns. The model part is shared across
 * all zombies, so the scale is rewritten every frame from the render state's charge — 0 restores the
 * normal shape. Charge 1 → ~2.3x girth on x/z, a touch on y.
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractZombieModel.class)
public class ZombieBellyModelMixin {

    /** How much charge=1 inflates the belly on x/z, on top of the base 1.0 scale — see class javadoc. */
    private static final float GIRTH_XZ_SCALE = 1.3f;
    /** How much charge=1 inflates the belly on y, on top of the base 1.0 scale — see class javadoc. */
    private static final float GIRTH_Y_SCALE = 0.35f;

    // require = 0: purely presentational — see com.dreykaoas.lethalbreed.client.PresentationalMixinNotes.
    @Inject(require = 0, method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)V",
            at = @At("TAIL"))
    private void lethalbreed$swellBelly(ZombieRenderState state, CallbackInfo ci) {
        float charge = ((ZombieRenderFlags) state).lethalbreed$bellyChargeDisplayed();
        ModelPart body = ((HumanoidModel<?>) (Object) this).body;
        float girth = 1.0f + charge * GIRTH_XZ_SCALE;
        body.xScale = girth;
        body.zScale = girth;
        body.yScale = 1.0f + charge * GIRTH_Y_SCALE;
    }
}
