package com.dreykaoas.lethalbreed.mixin.client.render;

import com.dreykaoas.lethalbreed.client.ZombieRenderFlags;
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

    // require = 0: purely presentational — see com.dreykaoas.lethalbreed.client.PresentationalMixinNotes.
    @Inject(require = 0, method = "extractRenderState", at = @At("TAIL"))
    private void lethalbreed$carryBellyCharge(LivingEntity entity, LivingEntityRenderState state,
                                              float partialTick, CallbackInfo ci) {
        float charge = entity instanceof Zombie
                ? entity.getAttachedOrElse(SpecialAttachment.BOMBEUR_CHARGE, 0.0f)
                : 0.0f;
        ZombieRenderFlags flags = (ZombieRenderFlags) state;
        flags.lethalbreed$bellyCharge(charge);
        flags.lethalbreed$bellyChargeDisplayed(smoothedBellyCharge(flags, charge));

        boolean sleeping = entity instanceof Zombie
                && entity.getAttachedOrElse(ZombieStateAttachment.SLEEPING, false);
        ((ZombieRenderFlags) state).lethalbreed$sleeping(sleeping);
    }

    /** Characteristic smoothing time (seconds): smaller = catches up faster. 0.15 closes a server-update gap
     *  (~0.25s at LOD HIGH) while staying imperceptible on a 1.5s fuse. */
    private static final float SMOOTH_TIME_CONSTANT = 0.15f;

    /** Pulls the displayed value toward the synced target by a factor depending on the real time elapsed
     *  since the last call — independent of framerate and of the server update rate, so it stays smooth
     *  regardless of either. Drops instantly if the target falls (the belly must never stay inflated on a
     *  zombie that just respawned). */
    private static float smoothedBellyCharge(ZombieRenderFlags flags, float target) {
        long now = System.nanoTime();
        long last = flags.lethalbreed$bellyChargeLastNanos();
        float displayed = flags.lethalbreed$bellyChargeDisplayed();
        flags.lethalbreed$bellyChargeLastNanos(now);
        if (target <= 0.0f) {
            return 0.0f; // no residual trail on a fresh / unarmed zombie
        }
        if (last == 0L || target < displayed) {
            return target; // first call, or the target dropped (new fuse): no backward trail
        }
        float dt = (now - last) / 1_000_000_000.0f;
        float t = Math.min(1.0f, dt / SMOOTH_TIME_CONSTANT);
        return displayed + (target - displayed) * t;
    }
}
