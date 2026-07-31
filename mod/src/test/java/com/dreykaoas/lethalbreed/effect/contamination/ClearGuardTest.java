package com.dreykaoas.lethalbreed.effect.contamination;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless characterization of {@link ClearGuard} — the thread marker that tells milk apart from
 * {@code /effect clear}, since both routes bottom out in the same {@code removeAllEffects()} call.
 *
 * <p>This guard had no direct test, which is the wrong shape of gap for what it is: a thread-local
 * armed and disarmed by a {@code try}/{@code finally} in a mixin. A guard left armed does not throw
 * or log — it silently makes the NEXT {@code /effect clear} on that thread fail to cure, one
 * player action later and nowhere near the code that leaked it. These tests pin the two properties
 * the mixin's javadoc relies on: the finally-disarm survives an exception, and one thread's arming
 * is invisible to another.
 */
class ClearGuardTest {

    @AfterEach
    void alwaysDisarm() {
        // A test that leaks the marker would silently corrupt every test after it in this JVM.
        ClearGuard.disarm();
    }

    @Test
    void isMilkIsFalseOnAFreshThread() {
        assertFalse(ClearGuard.isMilk(), "an unarmed thread must read as a command-originated clear");
    }

    @Test
    void armMakesItMilk() {
        ClearGuard.arm();
        assertTrue(ClearGuard.isMilk());
    }

    @Test
    void disarmClearsIt() {
        ClearGuard.arm();
        ClearGuard.disarm();
        assertFalse(ClearGuard.isMilk());
    }

    @Test
    void armIsAFlagNotACounter() {
        // Documents the actual semantics: a second arm() does not stack, so ONE disarm() clears both.
        // Nested milk consumption on a single thread would therefore disarm early. That cannot happen
        // today (the mixin redirects a single call and MilkKeepsPlagueMixin's own analysis proved the
        // nesting unreachable), but the day it becomes reachable this test is the one that fails.
        ClearGuard.arm();
        ClearGuard.arm();
        ClearGuard.disarm();
        assertFalse(ClearGuard.isMilk());
    }

    @Test
    void theMarkerIsPerThread() throws InterruptedException {
        ClearGuard.arm();
        AtomicBoolean seenFromOtherThread = new AtomicBoolean(true);
        Thread other = new Thread(() -> seenFromOtherThread.set(ClearGuard.isMilk()));
        other.start();
        other.join();

        assertFalse(seenFromOtherThread.get(),
                "arming on one thread must not mark another — client and server threads share this JVM "
                        + "on an integrated server");
        assertTrue(ClearGuard.isMilk(), "the arming thread keeps its own marker");
    }

    @Test
    void disarmIsSafeWithoutArm() {
        ClearGuard.disarm();
        assertFalse(ClearGuard.isMilk());
    }

    @Test
    void theFinallyPatternSurvivesAnExceptionMidClear() {
        // Reproduces the exact shape of MilkKeepsPlagueMixin's redirect. If removeAllEffects() throws,
        // the marker must still be gone — otherwise the next /effect clear on this thread is treated as
        // milk and silently refuses to cure.
        assertThrows(IllegalStateException.class, () -> {
            ClearGuard.arm();
            try {
                throw new IllegalStateException("simulated failure inside removeAllEffects()");
            } finally {
                ClearGuard.disarm();
            }
        });

        assertFalse(ClearGuard.isMilk(), "an exception must not leave the guard armed");
    }
}
