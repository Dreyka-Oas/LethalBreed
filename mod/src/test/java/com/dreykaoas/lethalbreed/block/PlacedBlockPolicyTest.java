package com.dreykaoas.lethalbreed.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless characterization of {@link PlacedBlockPolicy} — the lifetime rules for zombie-placed blocks.
 *
 * <p>This class exists so those rules ARE testable: they used to live inline in {@link PlacedBlockTracker},
 * which imports {@code Level}/{@code BlockPos}/{@code Blocks} and therefore cannot load under plain JUnit,
 * leaving the whole bridging/pillaring path without a single test. The retain-then-resolve contract below
 * is the one that keeps a zombie's dirt bridge over a ravine from becoming permanent terrain.
 */
class PlacedBlockPolicyTest {

    private static final long L = 600L; // the shipped default placedBlockLifetimeTicks

    @Test
    void expiredFiresExactlyAtTheLifetime() {
        assertFalse(PlacedBlockPolicy.expired(L - 1, L));
        assertTrue(PlacedBlockPolicy.expired(L, L));
        assertTrue(PlacedBlockPolicy.expired(L + 1, L));
        assertFalse(PlacedBlockPolicy.expired(0, L));
    }

    @Test
    void anUnloadedOverAgePlacementIsHeldNotDropped() {
        // The retain-then-resolve contract. While the chunk is gone we can neither read the block state nor
        // destroy it, so an over-age entry must be KEPT and resolved when the chunk returns. Dropping it here
        // would leave the dirt in the world forever — exactly what the tracker exists to prevent.
        assertTrue(PlacedBlockPolicy.expired(L * 9, L), "nine lifetimes is long past expiry");
        assertFalse(PlacedBlockPolicy.abandoned(L * 9, L), "...yet it is still held, not abandoned");
    }

    @Test
    void abandonedFiresExactlyAtTenLifetimes() {
        assertEquals(10L, PlacedBlockPolicy.ABANDON_FACTOR);
        assertFalse(PlacedBlockPolicy.abandoned(L * 10 - 1, L));
        assertTrue(PlacedBlockPolicy.abandoned(L * 10, L));
        assertTrue(PlacedBlockPolicy.abandoned(L * 10 + 1, L));
    }

    @Test
    void expiredIsIndependentOfAbandoned() {
        assertTrue(PlacedBlockPolicy.expired(L, L));
        assertFalse(PlacedBlockPolicy.abandoned(L, L));
    }

    @Test
    void crackStageRampsAcrossTheLifetimeAndNeverReachesTen() {
        assertEquals(0, PlacedBlockPolicy.crackStage(0, L));
        assertEquals(0, PlacedBlockPolicy.crackStage(L / 10 - 1, L));
        assertEquals(1, PlacedBlockPolicy.crackStage(L / 10, L));
        assertEquals(5, PlacedBlockPolicy.crackStage(L / 2, L));
        assertEquals(9, PlacedBlockPolicy.crackStage(L - 1, L));

        int prev = 0;
        for (long age = 0; age <= L * 2; age++) {
            int s = PlacedBlockPolicy.crackStage(age, L);
            assertTrue(s >= 0 && s <= 9, "stage out of the vanilla 0..9 range at age " + age + ": " + s);
            assertTrue(s >= prev, "the ramp must be monotonic; dropped at age " + age);
            prev = s;
        }
        // Past the lifetime the ramp saturates at 9 rather than rolling over to a nonexistent stage 10.
        assertEquals(9, PlacedBlockPolicy.crackStage(L, L));
        assertEquals(9, PlacedBlockPolicy.crackStage(Long.MAX_VALUE / 16, L));
    }

    @Test
    void degenerateLifetimesAreFlooredAtOneTick() {
        // placedBlockLifetimeTicks is operator-settable; a 0 would divide by zero in the crack ramp.
        assertEquals(1L, PlacedBlockPolicy.lifetime(1));
        assertEquals(1L, PlacedBlockPolicy.lifetime(0));
        assertEquals(1L, PlacedBlockPolicy.lifetime(-500));
        assertEquals(20L, PlacedBlockPolicy.lifetime(20));

        for (long configured : new long[] {1L, 0L, -1L, -72_000L, Long.MIN_VALUE}) {
            assertTrue(PlacedBlockPolicy.expired(1, configured));
            assertFalse(PlacedBlockPolicy.abandoned(1, configured));
            assertTrue(PlacedBlockPolicy.abandoned(10, configured));
            int s = PlacedBlockPolicy.crackStage(0, configured);
            assertEquals(0, s);
            assertEquals(9, PlacedBlockPolicy.crackStage(5, configured));
        }
    }

    @Test
    void theAbandonThresholdCannotOverflowAcrossTheConfiguredBounds() {
        // ConfigBoundsTable bounds placedBlockLifetimeTicks to [20, 72000], so lifetime * 10 tops out at
        // 720000 ticks (~10 in-game hours) — nowhere near a long overflow.
        for (long configured : new long[] {20L, 600L, 72_000L}) {
            long threshold = PlacedBlockPolicy.lifetime(configured) * PlacedBlockPolicy.ABANDON_FACTOR;
            assertTrue(threshold > 0, "abandon threshold went non-positive for lifetime " + configured);
            assertTrue(threshold <= 720_000L);
            assertFalse(PlacedBlockPolicy.abandoned(threshold - 1, configured));
            assertTrue(PlacedBlockPolicy.abandoned(threshold, configured));
        }
    }
}
