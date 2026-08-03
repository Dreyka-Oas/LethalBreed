package com.dreykaoas.lethalbreed.dev.contam;

import com.dreykaoas.lethalbreed.effect.contamination.ContaminationState;
import com.dreykaoas.lethalbreed.effect.contamination.ContaminationTick;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

/**
 * Reflective size reads over every static collection the plague keeps victims in.
 *
 * <p>Read reflectively rather than through new public accessors: these buffers are implementation details and
 * {@code src/main} should not grow API for a test. The dev source set is on the classpath (not the module
 * path), so {@code setAccessible} works.
 *
 * <p>The list is deliberately exhaustive and lives in ONE place. The original leak (audit #8) was exactly a
 * teardown that purged seven collections and missed the eighth because it lived in a sibling class — a probe
 * that enumerates them per-call-site would repeat that mistake.
 */
public final class PlagueCollections {
    private PlagueCollections() {}

    /** A size of -1 means the field could not be read at all — treated as "not empty", never as "clean". */
    public record Sizes(int snapshot, int tracked, int nextPulse, int nextSymptomRoll,
                        int latentSlowUntil, int nextEvolveRoll, int episodes, int hallucTimers) {

        public boolean allEmpty() {
            return snapshot == 0 && tracked == 0 && nextPulse == 0 && nextSymptomRoll == 0
                    && latentSlowUntil == 0 && nextEvolveRoll == 0 && episodes == 0 && hallucTimers == 0;
        }

        @Override
        public String toString() {
            return "tracked=" + tracked + " nextPulse=" + nextPulse + " nextSymptomRoll=" + nextSymptomRoll
                    + " latentSlowUntil=" + latentSlowUntil + " nextEvolveRoll=" + nextEvolveRoll
                    + " episodes=" + episodes + " hallucTimers=" + hallucTimers + " SNAPSHOT=" + snapshot;
        }
    }

    private static final String EPISODES_CLASS =
            "com.dreykaoas.lethalbreed.effect.contamination.symptom.ContaminationEpisodes";
    private static final String HALLUC_CLASS =
            "com.dreykaoas.lethalbreed.effect.contamination.symptom.ContaminationHallucination";

    public static Sizes sizes() {
        return new Sizes(
                snapshotSize(),
                ContaminationState.tracked.size(),
                ContaminationState.nextPulse.size(),
                ContaminationState.nextSymptomRoll.size(),
                ContaminationState.latentSlowUntil.size(),
                ContaminationState.nextEvolveRoll.size(),
                privateSize(EPISODES_CLASS, "episodes"),
                privateSize(HALLUC_CLASS, "hallucTimers"));
    }

    /** Reflective read of {@code ContaminationTick.SNAPSHOT}; -1 means the field could not be read at all. */
    public static int snapshotSize() {
        try {
            Field f = ContaminationTick.class.getDeclaredField("SNAPSHOT");
            f.setAccessible(true);
            return ((Collection<?>) f.get(null)).size();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return -1;
        }
    }

    /** Reflective size of a private static Map/Collection on a contamination class; -1 if unreadable. */
    private static int privateSize(String className, String fieldName) {
        try {
            Field f = Class.forName(className).getDeclaredField(fieldName);
            f.setAccessible(true);
            Object v = f.get(null);
            if (v instanceof Map<?, ?> m) {
                return m.size();
            }
            return v instanceof Collection<?> c ? c.size() : -1;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return -1;
        }
    }
}
