package com.dreykaoas.lethalbreed.mixin.client;

import com.dreykaoas.lethalbreed.client.BellyChargeHolder;
import com.dreykaoas.lethalbreed.entity.ZombieStateAttachment;
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
 * Copies synced zombie-only cosmetic state (BOMBEUR belly charge) onto the render
 * state each frame, so model mixins can react without touching the entity. Runs for every living entity
 * (cheap guard); non-zombies write the neutral defaults, resetting shared model parts on the next frame.
 */
@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    // require = 0: purely presentational. lethalbreed.mixins.json sets defaultRequire=1, which turns any
    // failed injection into a hard crash at load — correct for gameplay mixins, wrong here. A HUD or
    // render mod that redirects the same call should cost the player a visual effect, not the game.
    @Inject(require = 0, method = "extractRenderState", at = @At("TAIL"))
    private void lethalbreed$carryBellyCharge(LivingEntity entity, LivingEntityRenderState state,
                                              float partialTick, CallbackInfo ci) {
        float charge = entity instanceof Zombie
                ? entity.getAttachedOrElse(SpecialAttachment.BOMBEUR_CHARGE, 0.0f)
                : 0.0f;
        ((BellyChargeHolder) state).lethalbreed$bellyCharge(charge);

        boolean sleeping = entity instanceof Zombie
                && entity.getAttachedOrElse(ZombieStateAttachment.SLEEPING, false);
        ((BellyChargeHolder) state).lethalbreed$sleeping(sleeping);
    }
}
