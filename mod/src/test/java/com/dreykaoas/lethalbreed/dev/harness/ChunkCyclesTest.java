package com.dreykaoas.lethalbreed.dev.harness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The defect this class exists to make impossible: a harness that unforces a chunk, waits a guessed 20 ticks,
 * re-forces it, and counts a "reload" that never happened. Every assertion made about reload behaviour after
 * such a cycle is vacuous — and it reads as a PASS.
 *
 * <p>{@link ChunkCycles} is driven by the witness entity's runtime id (or {@link ChunkCycles#ABSENT}), so these
 * tests script the exact timings a real server produces — the sibling rig measured drops at 2, 35, 272 and
 * never — without starting one, and can also script the case a boolean could not express: the witness coming
 * back as the SAME object, which means no deserialisation happened at all.
 */
class ChunkCyclesTest {

    private static final int A = ChunkCycles.ABSENT;

    /** Drive the machine to a standstill, feeding ids from a script. Returns the action log. */
    private static String run(ChunkCycles c, int[] ids) {
        StringBuilder log = new StringBuilder();
        for (int t = 0; t < ids.length && !c.finished(); t++) {
            log.append(switch (c.step(t, ids[t])) {
                case RELEASE -> "R";
                case REFORCE -> "F";
                case NONE -> ".";
            });
        }
        return log.toString();
    }

    /** Present, gone for {@code gone} ticks, back as a NEW object, repeated. A fresh id per reload is what a
     *  real deserialisation produces: the entity counter is monotonic. */
    private static int[] script(int cycles, int gone, int back) {
        int[] p = new int[cycles * (gone + back) + 8];
        int i = 0;
        int id = 100;
        p[i++] = id;
        for (int c = 0; c < cycles; c++) {
            for (int g = 0; g < gone; g++) { p[i++] = A; }
            id++;
            for (int b = 0; b < back; b++) { p[i++] = id; }
        }
        while (i < p.length) { p[i++] = id; }
        return p;
    }

    private static int[] filled(int length, int value) {
        int[] p = new int[length];
        java.util.Arrays.fill(p, value);
        return p;
    }

    @Test
    void issuesTheReleaseOnItsFirstStep() {
        ChunkCycles c = new ChunkCycles(1, 100);
        assertEquals(ChunkCycles.Action.RELEASE, c.step(0, 100));
    }

    @Test
    void reForcesOnlyOnceTheWitnessHasActuallyLeftMemory() {
        ChunkCycles c = new ChunkCycles(1, 100);
        c.step(0, 100);
        assertEquals(ChunkCycles.Action.NONE, c.step(1, 100), "still resident — re-forcing now proves nothing");
        assertEquals(ChunkCycles.Action.NONE, c.step(40, 100), "20 ticks was the old guess; still resident");
        assertEquals(ChunkCycles.Action.REFORCE, c.step(41, A));
    }

    /** A round trip is only complete when the witness came BACK: the old rig never checked that at all, so a
     *  chunk that dropped and never returned still counted as a reload. */
    @Test
    void countsARoundTripOnlyAfterTheWitnessReturns() {
        ChunkCycles c = new ChunkCycles(1, 100);
        c.step(0, 100);
        c.step(5, A);                           // REFORCE issued
        assertEquals(0, c.completed(), "gone is half a round trip");
        c.step(9, 101);
        assertEquals(1, c.completed());
        assertTrue(c.finished());
    }

    /**
     * The assertion a boolean could never make. If the witness returns carrying the id it had before the
     * unforce, no new object was built — it was briefly missing from the lookup, not deserialised — and every
     * conclusion about reload behaviour drawn from it would be false.
     */
    @Test
    void aWitnessThatReturnsAsTheSameObjectIsNotAReload() {
        ChunkCycles c = new ChunkCycles(1, 100);
        c.step(0, 100);
        c.step(5, A);
        c.step(9, 100);                          // same id as before the unforce
        assertEquals(0, c.completed(), "no deserialisation happened, so nothing was exercised");
        assertEquals(1, c.staleReturns());
        assertTrue(c.diagnosis().contains("same object"), c.diagnosis());
    }

    @Test
    void aStaleReturnStillLetsTheNextCycleTry() {
        ChunkCycles c = new ChunkCycles(2, 100);
        c.step(0, 100);
        c.step(2, A);
        assertEquals(ChunkCycles.Action.RELEASE, c.step(4, 100), "same id back — try again, do not count it");
        c.step(6, A);
        c.step(8, 101);
        assertEquals(1, c.completed());
        assertEquals(1, c.staleReturns());
    }

    @Test
    void repeatsForTheRequestedNumberOfCycles() {
        ChunkCycles c = new ChunkCycles(3, 100);
        String log = run(c, script(3, 4, 3));
        assertEquals(3, c.completed());
        assertEquals(0, c.abandoned());
        assertEquals(3, log.chars().filter(ch -> ch == 'R').count(), "one release per cycle");
        assertEquals(3, log.chars().filter(ch -> ch == 'F').count(), "one re-force per cycle");
    }

    /** The measured worst case on this hardware: the chunk simply never leaves memory. It must be RECORDED,
     *  never counted, and the world must not be left with a dangling unforce. */
    @Test
    void abandonsAndReForcesWhenTheChunkNeverDrops() {
        ChunkCycles c = new ChunkCycles(2, 50);
        String log = run(c, filled(80, 100));

        assertEquals(0, c.completed(), "nothing was ever measured");
        assertEquals(1, c.abandoned());
        assertTrue(c.finished(), "retrying is pointless when the disk is the bottleneck");
        assertTrue(log.endsWith("F"), "the unforce must be undone or the arena stays unloaded for the eval");
    }

    /** Gone but never back is equally unmeasurable, and equally must not be counted. */
    @Test
    void abandonsWhenTheChunkNeverComesBack() {
        ChunkCycles c = new ChunkCycles(2, 30);
        int[] p = filled(80, A);
        p[0] = 100;
        run(c, p);
        assertEquals(0, c.completed());
        assertEquals(1, c.abandoned());
        assertTrue(c.finished());
    }

    /** A partial run is the honest outcome when the window is short — some real cycles beat five fake ones. */
    @Test
    void aSlowServerStillYieldsHonestPartialCoverage() {
        ChunkCycles c = new ChunkCycles(5, 300);
        run(c, script(2, 272, 30));             // only two round trips fit before the script ends
        assertEquals(2, c.completed());
        assertEquals(0, c.abandoned());
        assertFalse(c.finished(), "three cycles are still owed");
    }

    @Test
    void stepsAfterFinishingDoNothing() {
        ChunkCycles c = new ChunkCycles(1, 100);
        c.step(0, 100);
        c.step(1, A);
        c.step(2, 101);
        assertTrue(c.finished());
        assertEquals(ChunkCycles.Action.NONE, c.step(3, A));
        assertEquals(ChunkCycles.Action.NONE, c.step(4, 102));
        assertEquals(1, c.completed(), "a finished machine must not keep counting");
    }

    /** The string the verdict quotes. It has to distinguish "measured nothing" from "measured plenty",
     *  because that is the difference between a vacuous PASS and a real one. */
    @Test
    void diagnosisSeparatesMeasuredFromUnmeasured() {
        ChunkCycles never = new ChunkCycles(2, 20);
        run(never, filled(40, 100));
        assertTrue(never.diagnosis().contains("0 real"), never.diagnosis());
        assertTrue(never.diagnosis().contains("abandoned"), never.diagnosis());

        ChunkCycles good = new ChunkCycles(2, 100);
        run(good, script(2, 4, 3));
        assertTrue(good.diagnosis().contains("2 real"), good.diagnosis());
    }

    @Test
    void rejectsANonsenseConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkCycles(0, 100));
        assertThrows(IllegalArgumentException.class, () -> new ChunkCycles(2, 0));
    }
}
