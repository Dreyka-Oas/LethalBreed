package com.dreykaoas.lethalbreed.dev.probe;

import com.dreykaoas.lethalbreed.probe.DevProbe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code DevProbe}'s stage ids are plain {@code int} constants — deliberately, so a player jar never ships
 * an enum's constants, {@code values()} array or labels (see {@code DevProbe}'s class javadoc). That design
 * only works if each id stays equal to the matching {@link StageProfiler.Stage} constant's {@link
 * StageProfiler.Stage#ordinal()}: {@code DevSink#stage} takes the raw int straight from {@code DevProbe} and
 * indexes {@code StageProfiler}'s accumulator arrays with it directly (no {@code Stage} lookup on the hot
 * path), so nothing else checks the two orders still agree. A silent reorder of {@code Stage} would
 * mis-attribute every timing to the wrong label without a single compile error.
 *
 * <p>{@code DevProbeTest} cannot catch this: {@code DevProbe} lives in {@code src/main} and has no reference
 * to {@code Stage} at all. This test can only exist here, in {@code src/test}, because {@code build.gradle.kts}
 * puts the {@code dev} source set's output on the test classpath — see the comment above {@code
 * sourceSets.test} there — which is what makes both {@code DevProbe} (via {@code main}) and {@code
 * StageProfiler.Stage} (via {@code dev}) visible from the same test.
 */
class StageIdAlignmentTest {

    @Test
    void everyDevProbeStageIdMatchesTheStageEnumOrdinal() {
        assertEquals(StageProfiler.Stage.CLASSIFY.ordinal(), DevProbe.CLASSIFY, "CLASSIFY drifted");
        assertEquals(StageProfiler.Stage.GRID.ordinal(), DevProbe.GRID, "GRID drifted");
        assertEquals(StageProfiler.Stage.PACK.ordinal(), DevProbe.PACK, "PACK drifted");
        assertEquals(StageProfiler.Stage.SUNBURN.ordinal(), DevProbe.SUNBURN, "SUNBURN drifted");
        assertEquals(StageProfiler.Stage.MOOD.ordinal(), DevProbe.MOOD, "MOOD drifted");
        assertEquals(StageProfiler.Stage.TICK.ordinal(), DevProbe.TICK, "TICK drifted");
        assertEquals(StageProfiler.Stage.FLOWSNAP.ordinal(), DevProbe.FLOWSNAP, "FLOWSNAP drifted");
        assertEquals(StageProfiler.Stage.SCAN.ordinal(), DevProbe.SCAN, "SCAN drifted");
        assertEquals(StageProfiler.Stage.ORDER.ordinal(), DevProbe.ORDER, "ORDER drifted");
        assertEquals(StageProfiler.Stage.LOS.ordinal(), DevProbe.LOS, "LOS drifted");
    }

    @Test
    void stageCountMatchesTheEnumSize() {
        assertEquals(StageProfiler.Stage.values().length, DevProbe.STAGE_COUNT);
    }
}
