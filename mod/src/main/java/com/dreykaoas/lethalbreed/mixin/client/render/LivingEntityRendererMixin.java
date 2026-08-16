package com.dreykaoas.lethalbreed.mixin.client.render;

import com.dreykaoas.lethalbreed.client.BombeurBellySmoothing;
import com.dreykaoas.lethalbreed.client.ZombieRenderFlags;
import com.dreykaoas.lethalbreed.entity.ZombieStateAttachment;
import com.dreykaoas.lethalbreed.special.SpecialAttachment;
import com.dreykaoas.lethalbreed.special.runtime.BombeurBellySmoothingMath;
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
        // The smoothing STATE (previous displayed value + timestamp) is read/written on the entity, not on
        // `state`: LivingEntityRenderState is a brand-new object every frame (createRenderState() calls
        // `new ZombieRenderState()` before extractRenderState runs), so anything stored there can never
        // persist across frames. The client Zombie entity does persist, which is why BombeurBellySmoothing
        // lives there — see that interface's javadoc. `state` keeps its existing role as the final value
        // handed to the model, written once per frame below.
        flags.lethalbreed$bellyChargeDisplayed(smoothedBellyCharge(entity, charge));

        boolean sleeping = entity instanceof Zombie
                && entity.getAttachedOrElse(ZombieStateAttachment.SLEEPING, false);
        ((ZombieRenderFlags) state).lethalbreed$sleeping(sleeping);
    }

    /** Pulls the displayed belly charge toward {@code target}, using the previous value + timestamp
     *  persisted on {@code entity} (see {@link BombeurBellySmoothing}). Non-zombies have no such state to
     *  read and don't need smoothing — the target is already 0 for them. The actual maths live in
     *  {@link BombeurBellySmoothingMath}, pure and unit-tested independently of Minecraft. */
    private static float smoothedBellyCharge(LivingEntity entity, float target) {
        if (!(entity instanceof BombeurBellySmoothing smoothing)) {
            return target;
        }
        long now = System.nanoTime();
        long last = smoothing.lethalbreed$smoothedBellyChargeLastNanos();
        float displayed = smoothing.lethalbreed$smoothedBellyCharge();
        float dt = last == 0L ? 0.0f : (now - last) / 1_000_000_000.0f;
        float result = BombeurBellySmoothingMath.smooth(last != 0L, displayed, target, dt);
        smoothing.lethalbreed$smoothedBellyCharge(result);
        smoothing.lethalbreed$smoothedBellyChargeLastNanos(now);
        return result;
    }
}
