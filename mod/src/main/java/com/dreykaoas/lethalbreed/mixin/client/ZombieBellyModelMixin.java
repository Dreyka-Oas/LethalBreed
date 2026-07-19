package com.dreykaoas.lethalbreed.mixin.client;

import com.dreykaoas.lethalbreed.client.BellyChargeHolder;
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
 * Inflates the BOMBEUR's belly (the {@code body} part) as its fuse burns. The model part is shared across
 * all zombies, so the scale is rewritten every frame from the render state's charge — 0 restores the
 * normal shape. Charge 1 → ~2.3x girth on x/z, a touch on y.
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractZombieModel.class)
public class ZombieBellyModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)V",
            at = @At("TAIL"))
    private void lethalbreed$swellBelly(ZombieRenderState state, CallbackInfo ci) {
        float charge = ((BellyChargeHolder) state).lethalbreed$bellyCharge();
        ModelPart body = ((HumanoidModel<?>) (Object) this).body;
        float girth = 1.0f + charge * 1.3f;
        body.xScale = girth;
        body.zScale = girth;
        body.yScale = 1.0f + charge * 0.35f;
    }
}
