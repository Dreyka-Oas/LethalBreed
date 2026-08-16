package com.dreykaoas.lethalbreed.special;

import com.dreykaoas.lethalbreed.special.runtime.BombeurBellySmoothingMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down {@link BombeurBellySmoothingMath}, the maths behind the BOMBEUR belly-charge smoothing. Runs
 * without a server or a mixin loaded, precisely because that class carries no {@code net.minecraft} type and
 * takes {@code dt} as a parameter instead of touching {@code System.nanoTime()} — same separation as
 * {@link BombeurBlastTest} for {@code BombeurBlast}.
 *
 * <p>This also exercises the bug the smoothing was originally meant to fix: with the smoothing state stored
 * on {@code LivingEntityRenderState} (a fresh object every frame), every call looked like a first call
 * ({@code hasPrevious == false}) and {@link BombeurBellySmoothingMath#smooth} always returned {@code target}
 * unsmoothed. Simulating an actual sequence of calls with {@code hasPrevious} correctly threaded through (as
 * the persistent {@code BombeurBellySmoothing} entity state now does) is what proves the fix.
 */
class BombeurBellySmoothingMathTest {

    @Test
    void firstCallDoesNotJumpStraightToTheTarget() {
        // hasPrevious=false is the state on a genuinely fresh entity (never smoothed before) — that legitimately
        // jumps straight to target, by design (no history to smooth from). The bug was every frame looking like
        // this. A *second* call, with history now available and a small dt, must NOT recopy the target.
        float afterFirst = BombeurBellySmoothingMath.smooth(false, 0.0f, 0.5f, 0.0f);
        assertEquals(0.5f, afterFirst, 1e-6f, "first call establishes the baseline at the target");

        float afterSecond = BombeurBellySmoothingMath.smooth(true, afterFirst, 0.6f, 0.01f);
        assertTrue(afterSecond < 0.6f,
                "with history and a small dt, the displayed value must lag the target: got " + afterSecond);
        assertTrue(afterSecond > afterFirst,
                "it should still move toward the target: got " + afterSecond);
    }

    @Test
    void aLargeDtEffectivelyCatchesUpToTheTarget() {
        // dt >> SMOOTH_TIME_CONSTANT saturates t at 1.0, so a huge frame gap is allowed to reach the target.
        float result = BombeurBellySmoothingMath.smooth(true, 0.1f, 0.9f, 10.0f);
        assertEquals(0.9f, result, 1e-6f);
    }

    @Test
    void successiveCallsConvergeTowardTheTarget() {
        float displayed = 0.0f;
        boolean hasPrevious = false;
        float target = 0.8f;
        // 30 steps of 1/60s (0.5s of simulated real time) at SMOOTH_TIME_CONSTANT=0.15s is several time
        // constants — comfortably enough to converge.
        for (int i = 0; i < 30; i++) {
            displayed = BombeurBellySmoothingMath.smooth(hasPrevious, displayed, target, 1.0f / 60.0f);
            hasPrevious = true;
        }
        assertEquals(target, displayed, 0.01f, "should have converged close to the target after 0.5s");
    }

    @Test
    void droppingTargetToZeroSnapsBackImmediately() {
        // The belly must never keep looking inflated once the charge is genuinely gone (e.g. respawn).
        float result = BombeurBellySmoothingMath.smooth(true, 0.7f, 0.0f, 0.001f);
        assertEquals(0.0f, result, 1e-9f);
    }

    @Test
    void aNewShorterFuseDoesNotVisiblyDeflate() {
        // Target below the currently displayed value (a fresh, shorter fuse started) jumps straight down
        // rather than trailing off from the old, larger value.
        float result = BombeurBellySmoothingMath.smooth(true, 0.7f, 0.3f, 0.001f);
        assertEquals(0.3f, result, 1e-9f);
    }
}
