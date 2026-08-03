package com.dreykaoas.lethalbreed.dev.harness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule every harness got wrong by hand: wait for the CONDITION, not for a guessed number of ticks.
 *
 * <p>These run under plain JUnit because {@link TickWait} takes no Minecraft type — it is handed a boolean per
 * tick and nothing else. That is the whole point of the split: the timing rule is now testable at every
 * boundary (met on the last allowed tick, timed out one tick later, never flipping once decided) which a
 * dedicated-server run can neither force nor observe.
 */
class TickWaitTest {

    @Test
    void reportsPendingWhileTheConditionIsFalse() {
        TickWait w = new TickWait("chunk drop", 10);
        w.start(100);
        assertEquals(TickWait.Result.PENDING, w.poll(101, false));
        assertEquals(TickWait.Result.PENDING, w.poll(105, false));
    }

    @Test
    void reportsMetTheTickTheConditionHolds() {
        TickWait w = new TickWait("chunk drop", 10);
        w.start(100);
        w.poll(101, false);
        assertEquals(TickWait.Result.MET, w.poll(104, true));
        assertEquals(4, w.elapsed(), "elapsed must be measured from the arming tick");
    }

    /** A condition satisfied on the very last allowed tick is SATISFIED. Checking the deadline first would
     *  turn the most common near-miss into a spurious harness failure. */
    @Test
    void conditionOnTheDeadlineTickWins() {
        TickWait w = new TickWait("chunk drop", 10);
        w.start(100);
        assertEquals(TickWait.Result.MET, w.poll(110, true));
    }

    @Test
    void timesOutOneTickPastTheDeadline() {
        TickWait w = new TickWait("chunk drop", 10);
        w.start(100);
        assertEquals(TickWait.Result.PENDING, w.poll(109, false));
        assertEquals(TickWait.Result.TIMED_OUT, w.poll(110, false));
    }

    /** Once decided, a wait must never change its mind: a harness that polls one extra time after acting
     *  would otherwise re-trigger the action, or turn a recorded timeout into a pass. */
    @Test
    void staysDecidedOnceMet() {
        TickWait w = new TickWait("chunk drop", 10);
        w.start(100);
        w.poll(102, true);
        assertEquals(TickWait.Result.MET, w.poll(103, false));
        assertEquals(TickWait.Result.MET, w.poll(999, false));
        assertEquals(2, w.elapsed());
    }

    @Test
    void staysDecidedOnceTimedOut() {
        TickWait w = new TickWait("chunk drop", 10);
        w.start(100);
        w.poll(120, false);
        assertEquals(TickWait.Result.TIMED_OUT, w.poll(121, true),
                "a condition that arrives after we gave up does not retroactively rescue the measurement");
    }

    /** Re-arming is how a repeating cycle reuses one wait; it must clear the previous verdict. */
    @Test
    void restartClearsThePreviousVerdict() {
        TickWait w = new TickWait("chunk drop", 10);
        w.start(100);
        w.poll(101, true);
        w.start(200);
        assertEquals(TickWait.Result.PENDING, w.poll(201, false));
        assertEquals(TickWait.Result.MET, w.poll(203, true));
        assertEquals(3, w.elapsed());
    }

    /** Polling a wait nobody armed is a harness bug. Returning PENDING would hide it forever. */
    @Test
    void pollingBeforeStartIsProgrammerError() {
        TickWait w = new TickWait("chunk drop", 10);
        IllegalStateException boom = assertThrows(IllegalStateException.class, () -> w.poll(1, true));
        assertTrue(boom.getMessage().contains("chunk drop"));
    }

    @Test
    void aNonPositiveTimeoutIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TickWait("x", 0));
        assertThrows(IllegalArgumentException.class, () -> new TickWait("x", -1));
    }

    @Test
    void namesItselfInTheTimeoutDescription() {
        TickWait w = new TickWait("the probe's chunk to leave memory", 10);
        w.start(0);
        w.poll(11, false);
        assertTrue(w.describe().contains("the probe's chunk to leave memory"));
        assertTrue(w.describe().contains("10"), "the description must state the budget it exhausted");
    }
}
