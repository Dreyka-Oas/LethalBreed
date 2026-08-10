package com.dreykaoas.lethalbreed.pack;

import com.dreykaoas.lethalbreed.pack.PackState.Ghost;
import com.dreykaoas.lethalbreed.pack.PackState.Phase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackStateTest {

    private static PackState pack() {
        return new PackState(1L, 100.0, 200.0, 42L);
    }

    @Test
    void aNewPackStartsWhereItWasBornAndGoingNowhere() {
        PackState p = pack();
        assertEquals(100, p.destX);
        assertEquals(200, p.destZ);
        assertEquals(Phase.MATERIAL, p.phase);
        assertTrue(p.isEmpty());
    }

    @Test
    void aNewPackHasAUsableHeadingRatherThanAZeroVector() {
        // PackWander repairs a zero heading, but starting from a real one keeps the very first leg
        // meaningful instead of always due east by accident.
        PackState p = pack();
        assertEquals(1.0, Math.hypot(p.headingX, p.headingZ), 1e-9);
    }

    /** The three member states are summed, never double-counted — that sum is what the dissolve rule reads,
     *  and undercounting it would dissolve a pack whose members are merely off on disk. */
    @Test
    void membersAreCountedAcrossAllThreeStates() {
        PackState p = pack();
        p.liveIds.add(11);
        p.liveIds.add(12);
        p.ghosts.add(new Ghost(1L, 2L, new byte[0]));
        p.detached = 3;
        assertEquals(6, p.totalMembers());
        assertFalse(p.isEmpty());
    }

    @Test
    void aCorruptedDetachedCountCannotDriveTheTotalNegative() {
        // detached is decremented on re-join; a double decrement must not make an occupied pack read empty
        // and get dropped with its members still in the world.
        PackState p = pack();
        p.liveIds.add(11);
        p.detached = -5;
        assertEquals(1, p.totalMembers());
        assertFalse(p.isEmpty());
    }

    @Test
    void aFreshlyCapturedGhostStartsAtZeroRetries() {
        assertEquals(0, new Ghost(1L, 2L, new byte[0]).retries());
    }

    // ---- Materialiser give-up threshold ----

    @Test
    void aDisabledLimitNeverGivesUp() {
        // packMaterializeRetries <= 0 must reproduce the old, pre-cutoff behaviour: retried forever.
        assertFalse(PackState.retriesExhausted(0, 0));
        assertFalse(PackState.retriesExhausted(1_000_000, 0));
        assertFalse(PackState.retriesExhausted(5, -1));
    }

    @Test
    void aGhostIsKeptWhileBelowTheLimit() {
        assertFalse(PackState.retriesExhausted(4, 5));
    }

    @Test
    void aGhostIsDroppedOnceItReachesTheLimit() {
        // >= , not >: the sweep that pushes retries to the limit is the one that gives up, so a member is
        // never carried one extra sweep past the operator's configured budget.
        assertTrue(PackState.retriesExhausted(5, 5));
        assertTrue(PackState.retriesExhausted(6, 5));
    }
}
