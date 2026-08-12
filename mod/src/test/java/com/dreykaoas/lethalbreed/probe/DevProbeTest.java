package com.dreykaoas.lethalbreed.probe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seam's whole contract is "costs nothing and does nothing unless a dev sink was installed".
 * These tests pin that: an uninstalled probe is off, every gate is false, and no id collides.
 */
class DevProbeTest {

    /** Records what it was told, so a test can assert the seam forwards faithfully. */
    private static final class Recorder implements DevProbe.Sink {
        final List<String> events = new ArrayList<>();

        @Override public void stage(int stage, long nanos) { events.add("stage:" + stage + ":" + nanos); }
        @Override public void count(int counter, int entityId) { events.add("count:" + counter + ":" + entityId); }
        @Override public void trace(int channel, String message) { events.add("trace:" + channel + ":" + message); }
        @Override public void tickEnd(net.minecraft.server.MinecraftServer server, long tick, long nanos) {
            events.add("tick:" + tick + ":" + nanos);
        }
    }

    @AfterEach
    void resetSeam() {
        DevProbe.uninstall();
    }

    @Test
    void aFreshProbeIsOffAndHasNoSink() {
        assertNull(DevProbe.sink, "a player jar must never have a sink");
        assertFalse(DevProbe.on(), "on() must be false with no sink");
    }

    @Test
    void everyTraceChannelIsOffByDefault() {
        assertEquals(0, DevProbe.traceMask);
        assertFalse(DevProbe.tracing(DevProbe.CLIMB));
        assertFalse(DevProbe.tracing(DevProbe.PACKS));
        assertFalse(DevProbe.tracing(DevProbe.CONTAM));
    }

    @Test
    void installTurnsTheProbeOnAndEnablesOnlyTheRequestedChannels() {
        Recorder r = new Recorder();
        DevProbe.install(r, 1 << DevProbe.PACKS);

        assertTrue(DevProbe.on());
        assertFalse(DevProbe.tracing(DevProbe.CLIMB), "CLIMB was not requested");
        assertTrue(DevProbe.tracing(DevProbe.PACKS));
        assertFalse(DevProbe.tracing(DevProbe.CONTAM), "CONTAM was not requested");
    }

    @Test
    void uninstallRestoresThePlayerJarState() {
        DevProbe.install(new Recorder(), 0b111);
        DevProbe.uninstall();

        assertNull(DevProbe.sink);
        assertFalse(DevProbe.on());
        assertEquals(0, DevProbe.traceMask);
    }

    @Test
    void theSinkReceivesExactlyWhatTheCallSitePassed() {
        Recorder r = new Recorder();
        DevProbe.install(r, 0b111);

        DevProbe.sink.stage(DevProbe.FLOWSNAP, 4200L);
        DevProbe.sink.count(DevProbe.SHADE_SCAN, 77);
        DevProbe.sink.trace(DevProbe.CLIMB, "PILLAR");

        assertEquals(List.of("stage:6:4200", "count:4:77", "trace:0:PILLAR"), r.events);
    }

    @Test
    void stageIdsAreDenseAndUniqueAcrossTheDeclaredCount() {
        int[] ids = { DevProbe.CLASSIFY, DevProbe.GRID, DevProbe.PACK, DevProbe.SUNBURN, DevProbe.MOOD,
                      DevProbe.TICK, DevProbe.FLOWSNAP, DevProbe.SCAN, DevProbe.ORDER, DevProbe.LOS };
        assertEquals(DevProbe.STAGE_COUNT, ids.length);
        boolean[] seen = new boolean[DevProbe.STAGE_COUNT];
        for (int id : ids) {
            assertFalse(seen[id], "duplicate stage id " + id);
            seen[id] = true;
        }
    }

    @Test
    void counterIdsAreDenseAndUniqueAcrossTheDeclaredCount() {
        int[] ids = { DevProbe.INFECT, DevProbe.DEATH, DevProbe.SHELTER_SCAN,
                      DevProbe.DISTRESS, DevProbe.SHADE_SCAN };
        assertEquals(DevProbe.COUNTER_COUNT, ids.length);
        boolean[] seen = new boolean[DevProbe.COUNTER_COUNT];
        for (int id : ids) {
            assertFalse(seen[id], "duplicate counter id " + id);
            seen[id] = true;
        }
    }
}
