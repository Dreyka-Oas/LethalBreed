package com.dreykaoas.lethalbreed.entity.mood;

import com.dreykaoas.lethalbreed.entity.mood.sleep.ShadeStall;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deadlock this exists to break, as measured by the shade rig.
 *
 * <p>A zombie acquired a shade target six blocks east, walked five of them, and stopped one block short of the
 * roof — then stood there for the rest of the run: {@code pos=(153,101,460)} identical at t+40, t+80, t+120 …
 * t+320, with {@code hasTarget=true} and {@code seeking=true} the whole time. It never arrived, so it never
 * dozed; and because {@code handleDaySleep} returns early while a target is held ("already pathing to the shade
 * memory"), it never re-planned either. Outside the rig's rain the same zombie would be on fire throughout.
 *
 * <p>One run in three. The other two arrived normally, which is what makes a watchdog the right shape: arrival
 * is the normal case and must stay untouched, while the stall has to be detected and abandoned rather than
 * waited out.
 */
class ShadeStallTest {

    private static final int PATIENCE = 40;

    private static ShadeStall stall() {
        return new ShadeStall(PATIENCE);
    }

    @Test
    void aZombieClosingOnItsTargetIsNeverAbandoned() {
        ShadeStall s = stall();
        for (int t = 0; t < 200; t++) {
            double distSq = (100.0 - t * 0.4) * (100.0 - t * 0.4);
            assertFalse(s.stalled(t, distSq), "still closing at t=" + t);
        }
    }

    @Test
    void standingStillPastThePatienceWindowIsAStall() {
        ShadeStall s = stall();
        assertFalse(s.stalled(0, 2.25));
        assertFalse(s.stalled(PATIENCE - 1, 2.25));
        assertTrue(s.stalled(PATIENCE, 2.25), "no progress for the whole window — the walk is over");
    }

    /** The measured case: one block short, forever. */
    @Test
    void theOneBlockShortDeadlockIsCaught() {
        ShadeStall s = stall();
        boolean caught = false;
        for (int t = 0; t <= 320 && !caught; t++) {
            caught = s.stalled(t, 1.0);      // exactly one block away, never changing
        }
        assertTrue(caught, "the rig watched this for 280 ticks and nothing broke the deadlock");
    }

    /** Any real progress re-arms the patience: a slow walk with pauses must not be mistaken for a stall. */
    @Test
    void progressResetsThePatience() {
        ShadeStall s = stall();
        for (int t = 0; t < PATIENCE - 1; t++) {
            assertFalse(s.stalled(t, 36.0));
        }
        assertFalse(s.stalled(PATIENCE - 1, 25.0), "it moved a block — start counting again");
        // The fresh window runs from the tick of that progress, so it expires at (PATIENCE-1) + PATIENCE.
        for (int t = PATIENCE; t < 2 * PATIENCE - 1; t++) {
            assertFalse(s.stalled(t, 25.0), "inside the fresh window at t=" + t);
        }
        assertTrue(s.stalled(2 * PATIENCE - 1, 25.0));
    }

    /** Drifting AWAY is not progress. Being pushed off course must not buy unlimited patience. */
    @Test
    void movingFurtherAwayIsNotProgress() {
        ShadeStall s = stall();
        s.stalled(0, 25.0);
        for (int t = 1; t < PATIENCE; t++) {
            assertFalse(s.stalled(t, 25.0 + t));
        }
        assertTrue(s.stalled(PATIENCE, 100.0));
    }

    @Test
    void resetClearsEverythingForTheNextSeek() {
        ShadeStall s = stall();
        s.stalled(0, 4.0);
        assertTrue(s.stalled(PATIENCE, 4.0));
        s.reset();
        assertFalse(s.stalled(PATIENCE + 1, 4.0), "a fresh seek starts with its full patience");
        assertTrue(s.stalled(2 * PATIENCE + 1, 4.0));
    }

    @Test
    void aNonPositivePatienceIsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new ShadeStall(0));
    }
}
