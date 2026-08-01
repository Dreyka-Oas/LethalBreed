package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.dev.contam.ContamRig;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.effect.contamination.ContaminationState;
import com.dreykaoas.lethalbreed.effect.contamination.ContaminationTick;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.cow.Cow;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Collection;

/**
 * In-process proof that the plague's static collections release their victims — the checkable replacement for
 * the original "run jcmd and confirm exactly one ServerLevel after leaving the world" protocol.
 *
 * <p><b>Why that protocol was replaced.</b> It is meaningless on a dedicated server. There is no main menu to
 * return to, no world switch, and exactly one {@code ServerLevel} per dimension for the whole process
 * lifetime; the JVM exits at shutdown, so "the old level was collected after leaving" is not a state this
 * process can ever be in. Counting {@code ServerLevel} instances there would report PASS on a server that
 * leaks every single victim. What actually matters — and what actually leaked (audit #8) — is whether the
 * static holders drop their references. That is observable here, precisely, without a profiler.
 *
 * <p>Three distinct probes, in the order the defect happened:
 * <ol>
 *   <li>{@code snapshot-drained} — the reported scenario verbatim: a tracked victim is cured, {@code tracked}
 *       goes empty, and {@link ContaminationTick#tick} takes its early return. Its scratch buffer must have
 *       been cleared BEFORE that return, not after it. Read reflectively rather than through a new public
 *       accessor: the buffer is an implementation detail and {@code src/main} should not grow API for a test.
 *       The dev source set is on the classpath (not the module path), so {@code setAccessible} works.</li>
 *   <li>{@code victim-collectable} — the discarded entity itself becomes weakly reachable.</li>
 *   <li>{@code teardown-purges-all} — {@code onServerStopped()} empties EVERY collection. This is the check
 *       that would have caught the original bug: teardown purged seven collections and missed the eighth
 *       because it lived in a sibling class.</li>
 * </ol>
 */
public final class LeakProbeHarness {
    private LeakProbeHarness() {}

    private static final String SUITE = "plague";

    private static final int CX = 150;
    private static final int CZ = ArenaBuilder.VERIFY_BAND_Z;

    private static final int CURE_TICK = 60;
    private static final int SNAPSHOT_TICK = 61;
    private static final int DISCARD_TICK = 62;
    private static final int GC_UNTIL_TICK = 120;
    private static final int TEARDOWN_TICK = 130;

    private static int tick = -1;
    private static Cow victim;
    private static WeakReference<Cow> victimRef;
    private static int gcRounds;

    public static void onTick(MinecraftServer server) {
        if (!ProgressionConfig.devPlagueTest || !FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        if (server.getTickCount() < ContamRig.LEAK_START) {
            return;
        }
        tick++;
        ServerLevel ow = server.overworld();
        if (tick == 5) {
            setUp(ow);
        } else if (tick == CURE_TICK) {
            // tracked goes empty -> the next ContaminationTick.tick takes its early return. Exactly the path
            // that used to strand a whole batch of victims in the static scratch buffer.
            if (victim != null) {
                ContaminationManager.clearPlague(victim);
            }
        } else if (tick == SNAPSHOT_TICK) {
            int n = snapshotSize();
            DevVerdict.check(SUITE, "snapshot-drained", n == 0,
                    "ContaminationTick.SNAPSHOT.size()=" + n + " one tick after the last victim was cured "
                            + "(tracked=" + ContaminationState.tracked.size() + ", i.e. the early-return path)");
        } else if (tick == DISCARD_TICK) {
            discard(ow);
        } else if (tick > DISCARD_TICK && tick <= GC_UNTIL_TICK) {
            if ((tick - DISCARD_TICK) % 20 == 0) {
                System.gc();
                gcRounds++;
            }
            if (tick == GC_UNTIL_TICK) {
                collectability();
            }
        } else if (tick == TEARDOWN_TICK) {
            teardown();
        }
    }

    private static void setUp(ServerLevel ow) {
        ContaminationConfig.contaminationEnabled = true;
        ContamRig.arena(ow, CX, CZ, 4, 4);
        victim = ContamRig.cow(ow, CX, CZ, 0.0f);
        if (victim == null) {
            return;
        }
        ContaminationManager.contaminate(victim);
        ContaminationManager.forceSymptomatic(victim);
        victimRef = new WeakReference<>(victim);
    }

    private static void discard(ServerLevel ow) {
        if (victim != null) {
            victim.discard();
        }
        // Drop OUR strong reference too, or the probe below measures this field, not the mod's collections.
        victim = null;
        ContamRig.release(ow, CX, CZ);
    }

    private static void collectability() {
        boolean gone = victimRef != null && victimRef.get() == null;
        // System.gc() is a HINT: the JVM may decline, and a discarded entity can also linger one extra pass in
        // the level's own entity storage. A FAIL here is therefore weak evidence on its own and must be read
        // alongside teardown-purges-all, which is deterministic. Reported as a real check because on this JVM
        // and this schedule it is reproducible; if it ever starts flapping, demote it rather than widening it.
        DevVerdict.check(SUITE, "victim-collectable", gone,
                "weakRef.get()==null after " + gcRounds + " System.gc() rounds over "
                        + (GC_UNTIL_TICK - DISCARD_TICK) + " ticks — result=" + (gone ? "collected" : "STILL REACHABLE")
                        + " (System.gc() is a hint, not a guarantee)");
    }

    private static void teardown() {
        ContaminationManager.onServerStopped();
        int snap = snapshotSize();
        int tracked = ContaminationState.tracked.size();
        int pulse = ContaminationState.nextPulse.size();
        int symptom = ContaminationState.nextSymptomRoll.size();
        int slow = ContaminationState.latentSlowUntil.size();
        int evolve = ContaminationState.nextEvolveRoll.size();
        int episodes = privateMapSize("com.dreykaoas.lethalbreed.effect.contamination.ContaminationEpisodes", "episodes");
        int halluc = privateMapSize("com.dreykaoas.lethalbreed.effect.contamination.ContaminationHallucination", "hallucTimers");
        int total = snap + tracked + pulse + symptom + slow + evolve + episodes + halluc;
        DevVerdict.check(SUITE, "teardown-purges-all", total == 0 && snap >= 0 && episodes >= 0 && halluc >= 0,
                "after onServerStopped(): tracked=" + tracked + " nextPulse=" + pulse + " nextSymptomRoll=" + symptom
                        + " latentSlowUntil=" + slow + " nextEvolveRoll=" + evolve + " episodes=" + episodes
                        + " hallucTimers=" + halluc + " SNAPSHOT=" + snap);
    }

    /** Reflective read of {@code ContaminationTick.SNAPSHOT}; -1 means the field could not be read at all. */
    private static int snapshotSize() {
        try {
            Field f = ContaminationTick.class.getDeclaredField("SNAPSHOT");
            f.setAccessible(true);
            return ((Collection<?>) f.get(null)).size();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return -1;
        }
    }

    /** Reflective size of a private static Map/Set on a contamination class; -1 if unreadable. */
    private static int privateMapSize(String className, String fieldName) {
        try {
            Field f = Class.forName(className).getDeclaredField(fieldName);
            f.setAccessible(true);
            Object v = f.get(null);
            if (v instanceof java.util.Map<?, ?> m) {
                return m.size();
            }
            return v instanceof Collection<?> c ? c.size() : -1;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return -1;
        }
    }
}
