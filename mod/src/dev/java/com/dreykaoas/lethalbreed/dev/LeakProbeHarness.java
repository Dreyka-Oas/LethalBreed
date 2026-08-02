package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.config.ConfigOverride;
import com.dreykaoas.lethalbreed.config.domain.DevTestConfig;
import com.dreykaoas.lethalbreed.dev.contam.ContamRig;
import com.dreykaoas.lethalbreed.dev.contam.PlagueCollections;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.effect.contamination.ContaminationState;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.cow.Cow;

import java.lang.ref.WeakReference;

/**
 * In-process proof that the plague's static collections release their victims — the checkable replacement for
 * the original "run jcmd and confirm exactly one ServerLevel after leaving the world" protocol.
 *
 * <p><b>Why that protocol was replaced.</b> It is meaningless on a dedicated server. There is no main menu to
 * return to, no world switch, and exactly one {@code ServerLevel} per dimension for the whole process
 * lifetime; the JVM exits at shutdown, so "the old level was collected after leaving" is not a state this
 * process can ever be in. Counting {@code ServerLevel} instances there would report PASS on a server that
 * leaks every single victim. What actually matters — and what actually leaked (audit #8) — is whether the
 * static holders drop their references. That is observable here, precisely, without a profiler. The reflective
 * reads live in {@link PlagueCollections}.
 *
 * <p>Three distinct probes, in the order the defect happened:
 * <ol>
 *   <li>{@code snapshot-drained} — the reported scenario verbatim: a tracked victim is cured, {@code tracked}
 *       goes empty, and {@code ContaminationTick.tick} takes its early return. Its scratch buffer must have
 *       been cleared BEFORE that return, not after it.</li>
 *   <li>{@code victim-collectable} — the discarded entity itself becomes weakly reachable.</li>
 *   <li>{@code teardown-purges-all} — {@code onServerStopped()} empties EVERY collection. This is the check
 *       that would have caught the original bug: teardown purged seven collections and missed the eighth
 *       because it lived in a sibling class.</li>
 * </ol>
 */
public final class LeakProbeHarness extends TickPhasedHarness {

    public static final LeakProbeHarness INSTANCE = new LeakProbeHarness();

    private static final int CX = 150;
    private static final int CZ = ArenaBuilder.VERIFY_BAND_Z;

    private static final int SETUP_TICK = 5;
    private static final int CURE_TICK = 60;
    private static final int SNAPSHOT_TICK = 61;
    private static final int DISCARD_TICK = 62;
    private static final int GC_UNTIL_TICK = 120;
    private static final int TEARDOWN_TICK = 130;
    /** One forced collection every this many ticks between discard and the collectability read. */
    private static final int GC_PERIOD = 20;

    private Cow victim;
    private WeakReference<Cow> victimRef;
    private int gcRounds;

    private LeakProbeHarness() {
        super("plague", false, new Stage("leak", SETUP_TICK, TEARDOWN_TICK));
    }

    @Override
    protected boolean enabled() {
        return DevTestConfig.devPlagueTest;
    }

    @Override
    protected int startAfterServerTick() {
        return ContamRig.LEAK_START;
    }

    @Override
    protected void build(int stage, ServerLevel ow, MinecraftServer server, ConfigOverride cfg) {
        cfg.set("contaminationEnabled", true);
        ContamRig.arena(ow, CX, CZ, 4, 4);
        victim = ContamRig.cow(ow, CX, CZ, 0.0f);
        if (victim == null) {
            return;
        }
        ContaminationManager.contaminate(victim);
        ContaminationManager.forceSymptomatic(victim);
        victimRef = new WeakReference<>(victim);
    }

    @Override
    protected void observe(int stage, ServerLevel ow, int tick) {
        if (tick == CURE_TICK) {
            // tracked goes empty -> the next ContaminationTick.tick takes its early return. Exactly the path
            // that used to strand a whole batch of victims in the static scratch buffer.
            if (victim != null) {
                ContaminationManager.clearPlague(victim);
            }
        } else if (tick == SNAPSHOT_TICK) {
            int n = PlagueCollections.snapshotSize();
            check("snapshot-drained", n == 0,
                    "ContaminationTick.SNAPSHOT.size()=" + n + " one tick after the last victim was cured "
                            + "(tracked=" + ContaminationState.tracked.size() + ", i.e. the early-return path)");
        } else if (tick == DISCARD_TICK) {
            if (victim != null) {
                victim.discard();
            }
            // Drop OUR strong reference too, or the probe below measures this field, not the mod's collections.
            victim = null;
            ContamRig.release(ow, CX, CZ);
        } else if (tick > DISCARD_TICK && tick <= GC_UNTIL_TICK) {
            if ((tick - DISCARD_TICK) % GC_PERIOD == 0) {
                System.gc();
                gcRounds++;
            }
            if (tick == GC_UNTIL_TICK) {
                collectability();
            }
        }
    }

    private void collectability() {
        boolean gone = victimRef != null && victimRef.get() == null;
        // System.gc() is a HINT: the JVM may decline, and a discarded entity can also linger one extra pass in
        // the level's own entity storage. A FAIL here is therefore weak evidence on its own and must be read
        // alongside teardown-purges-all, which is deterministic. Reported as a real check because on this JVM
        // and this schedule it is reproducible; if it ever starts flapping, demote it rather than widening it.
        check("victim-collectable", gone,
                "weakRef.get()==null after " + gcRounds + " System.gc() rounds over "
                        + (GC_UNTIL_TICK - DISCARD_TICK) + " ticks — result=" + (gone ? "collected" : "STILL REACHABLE")
                        + " (System.gc() is a hint, not a guarantee)");
    }

    @Override
    protected void evaluate(int stage, ServerLevel ow, MinecraftServer server) {
        ContaminationManager.onServerStopped();
        PlagueCollections.Sizes s = PlagueCollections.sizes();
        check("teardown-purges-all", s.allEmpty(), "after onServerStopped(): " + s);
    }
}
