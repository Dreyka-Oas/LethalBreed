package com.dreykaoas.lethalbreed.client;

/**
 * Duck interface mixed into {@code LivingEntityRenderState}, carrying every mod-specific render flag from
 * the entity (read in an {@code extractRenderState} hook) to the client models and layers, which have no
 * access to the entity itself post render-state refactor. Currently three unrelated flags: the BOMBEUR belly
 * charge, the hallucinate-as-zombie swap and the day-sleeping pose. Non-zombies leave them all at their
 * neutral value.
 *
 * <p>Add a new render-state accessor HERE (not in a mixin class) and populate it in the matching extract hook.
 *
 * <p>Lives OUTSIDE the {@code mixin} package on purpose: a type inside a declared mixin package cannot be
 * referenced directly by transformed code (Mixin throws {@code IllegalClassLoadError}).
 */
public interface ZombieRenderFlags {
    /** BOMBEUR belly charge in {@code [0, 1]}, read in {@code LivingEntityRendererMixin.extractRenderState}
     *  and consumed by {@code ZombieBellyModelMixin.setupAnim}. 0 for everything that is not charging. */
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
