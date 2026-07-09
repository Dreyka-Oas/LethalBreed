package com.dreykaoas.lethalbreed.mixin.client;

import com.dreykaoas.lethalbreed.client.BellyChargeHolder;
import com.dreykaoas.lethalbreed.special.SpecialAttachment;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;

/**
 * Copies the synced BOMBEUR belly charge from a zombie onto its render state each frame, so the model
 * mixin can inflate the body without touching the entity. Runs for every living entity (cheap guard);
 * non-zombies write 0, which resets the shared model part's scale to normal on the next frame.
 */
@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void lethalbreed$carryBellyCharge(LivingEntity entity, LivingEntityRenderState state,
                                              float partialTick, CallbackInfo ci) {
        float charge = entity instanceof Zombie
                ? entity.getAttachedOrElse(SpecialAttachment.BOMBEUR_CHARGE, 0.0f)
                : 0.0f;
        ((BellyChargeHolder) state).lethalbreed$bellyCharge(charge);
    }
}
