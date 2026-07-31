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

    /** True when this render state belongs to a player the local (symptomatic) viewer is hallucinating as a
     *  zombie — swap texture + arm pose. Set in the AvatarRenderer extract hook, read in texture/model hooks. */
    boolean lethalbreed$hallucinateZombie();

    void lethalbreed$hallucinateZombie(boolean on);

    /** True when the zombie this render state belongs to is day-sleeping — pose it asleep (arms down, eyes
     *  closed). Set in {@code LivingEntityRendererMixin.extractRenderState}, read in the zombie arms/eyes hooks. */
    boolean lethalbreed$sleeping();

    void lethalbreed$sleeping(boolean sleeping);
}
