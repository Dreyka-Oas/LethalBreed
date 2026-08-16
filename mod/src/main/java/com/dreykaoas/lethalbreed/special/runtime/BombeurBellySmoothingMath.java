package com.dreykaoas.lethalbreed.special.runtime;

/**
 * Pure maths behind the client-side smoothing of the BOMBEUR belly charge: pulls the displayed value toward
 * a target by a factor depending on the real time elapsed since the last call, so the belly swells
 * continuously between the server's infrequent charge updates instead of visibly stepping.
 *
 * <p>Deliberately free of every {@code net.minecraft} type and of {@code System.nanoTime()} — {@code dt} is
 * passed in rather than measured internally — which is what lets this be unit-tested without booting a
 * client. Same separation as {@link BombeurBlast}; the caller ({@code LivingEntityRendererMixin}) owns the
 * actual clock and the persistent storage (the render state is recreated every frame, so the previous
 * displayed value and timestamp must live on the entity instead — see {@code BombeurBellySmoothing}).
 */
public final class BombeurBellySmoothingMath {
    private BombeurBellySmoothingMath() {}

    /** Characteristic smoothing time (seconds): smaller = catches up faster. 0.15 closes a server-update gap
     *  (~0.25s at LOD HIGH) while staying imperceptible on a 1.5s fuse. */
    public static final float SMOOTH_TIME_CONSTANT = 0.15f;

    /**
     * Pulls {@code displayed} toward {@code target} by a factor depending on {@code dt} (seconds elapsed
     * since the last call) — independent of framerate and of the server update rate. Drops instantly to 0
     * when the target is 0 (no residual trail on a fresh / unarmed zombie), and jumps straight to the
     * target on the first call ({@code hasPrevious == false}) or when the target falls below the currently
     * displayed value (a new, shorter fuse must not visibly deflate).
     */
    public static float smooth(boolean hasPrevious, float displayed, float target, float dt) {
        if (target <= 0.0f) {
            return 0.0f;
        }
        if (!hasPrevious || target < displayed) {
            return target;
        }
        float t = Math.min(1.0f, dt / SMOOTH_TIME_CONSTANT);
        return displayed + (target - displayed) * t;
    }
}
