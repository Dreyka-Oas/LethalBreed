package com.dreykaoas.lethalbreed.client;

/**
 * Duck interface mixed into the client-side {@code Zombie} entity, carrying the state needed to smooth the
 * BOMBER belly charge across frames: the last displayed (smoothed) value, and the timestamp of the last
 * smoothing computation.
 *
 * <p>This must live on the ENTITY, not on {@code LivingEntityRenderState} ({@link ZombieRenderFlags}): the
 * render state is a brand-new object every frame ({@code createRenderState()} calls {@code new
 * ZombieRenderState()} before {@code extractRenderState} populates it), so anything stored there resets to
 * its default every single frame and can never smooth across frames. The client {@code Zombie} entity, by
 * contrast, is not recreated per frame — it is the one object this smoothing can actually persist on.
 *
 * <p>Lives OUTSIDE the {@code mixin} package on purpose: a type inside a declared mixin package cannot be
 * referenced directly by transformed code (Mixin throws {@code IllegalClassLoadError}).
 */
public interface BomberBellySmoothing {
    /** Last smoothed (displayed) belly charge computed for this entity. 0 until the first computation. */
    float lethalbreed$smoothedBellyCharge();

    void lethalbreed$smoothedBellyCharge(float charge);

    /** Timestamp ({@code System.nanoTime()}) of the last smoothing computation — 0 until a computation has
     *  happened for this entity. */
    long lethalbreed$smoothedBellyChargeLastNanos();

    void lethalbreed$smoothedBellyChargeLastNanos(long nanos);
}
