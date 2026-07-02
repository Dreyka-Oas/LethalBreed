package com.dreykaoas.lethalbreed.client;

/**
 * Duck interface mixed into {@code LivingEntityRenderState} so the BOMBEUR belly charge (0..1) can ride
 * from the entity (read in {@code LivingEntityRendererMixin.extractRenderState}) to the client model
 * ({@code ZombieBellyModelMixin.setupAnim}), which has no access to the entity itself post render-state
 * refactor. Non-zombies leave it at 0.
 *
 * <p>Lives OUTSIDE the {@code mixin} package on purpose: a type inside a declared mixin package cannot be
 * referenced directly by transformed code (Mixin throws {@code IllegalClassLoadError}).
 */
public interface BellyChargeHolder {
    float lethalbreed$bellyCharge();

    void lethalbreed$bellyCharge(float charge);
}
