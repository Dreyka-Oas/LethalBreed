package com.dreykaoas.lethalbreed.mixin.client.render;

import com.dreykaoas.lethalbreed.mixin.client.model.PlayerModelZombieArmsMixin;

import com.dreykaoas.lethalbreed.client.ZombieRenderFlags;
import com.dreykaoas.lethalbreed.client.ZombieHallucination;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Plague hallucination (client-only): while the local player is symptomatic, draw every OTHER player with the
 * zombie skin. In 1.21.9+ the player texture is NOT resolved through {@code getTextureLocation} any more — it lives
 * on {@link AvatarRenderState#skin} (a {@code PlayerSkin} record, fed to the render type). So we tag the state and,
 * for tagged states, swap its skin's body texture for the zombie skin right at extraction. Arm pose is forced to
 * the classic straight-arm zombie stance in {@code PlayerModelZombieArmsMixin}. No packet, only the sick viewer.
 */
@Environment(EnvType.CLIENT)
@Mixin(net.minecraft.client.renderer.entity.player.AvatarRenderer.class)
public abstract class AvatarRendererHallucinationMixin {

    // require = 0: purely presentational — see com.dreykaoas.lethalbreed.client.PresentationalMixinNotes.
    @Inject(require = 0, method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void lethalbreed$tagHallucination(net.minecraft.world.entity.Avatar entity, AvatarRenderState state,
                                              float partialTick, CallbackInfo ci) {
        boolean on = entity instanceof AbstractClientPlayer player
                && ZombieHallucination.shouldHallucinate(player);
        ((ZombieRenderFlags) state).lethalbreed$hallucinateZombie(on);
        if (on) {
            // 1.21.9+ resolves player texture from state.skin (a PlayerSkin record), not getTextureLocation.
            state.skin = ZombieHallucination.zombieSkin(state.skin);
        }
    }

    /** Hide the floating username above a hallucinated zombie — a zombie has no name tag. */
    @Inject(require = 0, method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
    private void lethalbreed$hideName(net.minecraft.world.entity.Avatar entity, double distanceSq,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof AbstractClientPlayer player && ZombieHallucination.shouldHallucinate(player)) {
            cir.setReturnValue(false);
        }
    }
}
