package com.dreykaoas.lethalbreed.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackTetherTest {

    @Test
    void aFreshZombieBelongsToNoPack() {
        PackTether t = new PackTether();
        assertFalse(t.inPack());
        assertEquals(PackJoinRule.NO_PACK, t.packId());
        assertFalse(t.hasWaypoint());
    }

    @Test
    void joiningRecordsThePack() {
        PackTether t = new PackTether();
        t.setPackId(7L);
        assertTrue(t.inPack());
        assertEquals(7L, t.packId());
    }

    @Test
    void joiningResetsAnyStrayCountCarriedFromTheOldPack() {
        PackTether t = new PackTether();
        t.setPackId(1L);
        t.setStrayCount(2);
        t.setPackId(2L);
        assertEquals(0, t.strayCount());
    }

    /**
     * Leaving must drop the waypoint. A stale waypoint is not cosmetic: LodManager keeps a zombie out of
     * FROZEN as long as it has somewhere to walk, so a departed member would tick at full price forever,
     * walking to a rendezvous its former pack has long since left.
     */
    @Test
    void leavingDropsTheWaypoint() {
        PackTether t = new PackTether();
        t.setPackId(7L);
        t.setWaypoint(10, 64, 20);
        assertTrue(t.hasWaypoint());

        t.setPackId(PackJoinRule.NO_PACK);
        assertFalse(t.hasWaypoint());
    }

    @Test
    void movingBetweenPacksKeepsTheWaypointForTheNewOneToOverwrite() {
        PackTether t = new PackTether();
        t.setPackId(1L);
        t.setWaypoint(10, 64, 20);
        t.setPackId(2L);
        // Always armed: the waypoint is rewritten on the next pass by the new pack, and clearing it here
        // would freeze the member for the length of the interval.
        assertTrue(t.hasWaypoint());
    }

    @Test
    void theWaypointIsReadBackExactly() {
        PackTether t = new PackTether();
        t.setWaypoint(-1.5, 70.25, 300.75);
        assertEquals(-1.5, t.wpX(), 0.0);
        assertEquals(70.25, t.wpY(), 0.0);
        assertEquals(300.75, t.wpZ(), 0.0);
    }
}
