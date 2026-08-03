package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.domain.engine.DevTestConfig;
import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single PASS/FAIL contract shared by every headless verification harness. One grep-able prefix
 * ({@code [LB-Verify]}), one line shape per check, one terminal summary per suite:
 *
 * <pre>
 * [LB-Verify] climb/reaches-roof : PASS (arrived in 240 ticks)
 * [LB-Verify] climb/window-skip  : FAIL (hopped into the gap at y=105)
 * [LB-Verify] SUITE climb : 6/7 PASS — 1 FAILED
 * [LB-Verify] ALL DONE
 * </pre>
 *
 * <p><b>Why {@code ALL DONE} exists.</b> A harness that crashes (or a server that dies) BEFORE its
 * evaluation tick produces a log that is byte-identical to a clean run in which every check happened to
 * be silent. Absence of FAIL is therefore NOT evidence of success. {@code ALL DONE} is the positive
 * marker a scripted run must assert on: no {@code ALL DONE}, no verdict — treat the run as a failure
 * regardless of what else the log says.
 *
 * <p><b>Scope of "all".</b> This is deliberately a single-suite-per-run model: {@link DevTestSelector}
 * forces exactly one suite's config flag on and every other one off, precisely because the arena
 * harnesses build overlapping geometry. So "every registered suite has reported" and "this suite has
 * reported" are the same statement, and {@code ALL DONE} is emitted at the end of {@link #summary}.
 * Tracking a registry of expected suites would be pure ceremony under that constraint. If the model ever
 * grows to genuinely concurrent suites, this is the one place that has to change: gate the
 * {@code ALL DONE} emission on a completed-suite count instead of emitting it unconditionally.
 *
 * <p>{@link #summary} also honours {@link DevTestConfig#devAutoHalt}: when set, the server halts
 * itself right after the verdict, so a scripted {@code gradlew runServer} exits on its own instead of
 * hanging until the harness driver's timeout.
 */
public final class DevVerdict {
    private DevVerdict() {}

    /** Common prefix — the whole point is that a CI script can grep for exactly this token. */
    public static final String PREFIX = "[LB-Verify]";

    /** Per-suite tally: total checks and failures, in first-reported order. Server-thread only. */
    private static final Map<String, int[]> TALLIES = new LinkedHashMap<>();

    /**
     * Record and log one check. {@code suite} is the harness name ("climb", "breach", …), {@code name}
     * the individual assertion, {@code detail} the measured evidence that justifies the verdict — always
     * include the numbers, never just restate the check name.
     */
    public static void check(String suite, String name, boolean pass, String detail) {
        int[] tally = TALLIES.computeIfAbsent(suite, k -> new int[2]);
        tally[0]++;
        if (!pass) {
            tally[1]++;
        }
        LethalBreed.LOGGER.info("{} {}/{} : {} ({})", PREFIX, suite, name, pass ? "PASS" : "FAIL", detail);
    }

    /**
     * Emit the terminal tally for {@code suite}, then the load-bearing {@code ALL DONE} marker, then halt
     * the server if {@link DevTestConfig#devAutoHalt} is on. Call exactly once, on the server thread,
     * after the suite's last {@link #check}.
     *
     * @param server the running server; may be null when a suite has no handle to one (nothing is halted).
     */
    public static void summary(String suite, MinecraftServer server) {
        int[] tally = TALLIES.getOrDefault(suite, new int[2]);
        int total = tally[0];
        int failed = tally[1];
        int passed = total - failed;
        LethalBreed.LOGGER.info("{} SUITE {} : {}/{} PASS — {}", PREFIX, suite, passed, total,
                failed == 0 ? "0 FAILED" : failed + " FAILED");
        // Emitted unconditionally here (see class javadoc): one suite runs per server start, so reaching
        // this point IS "every registered suite has reported". Nothing after it may be silent-on-success.
        LethalBreed.LOGGER.info("{} ALL DONE", PREFIX);

        if (DevTestConfig.devAutoHalt && server != null) {
            LethalBreed.LOGGER.info("{} devAutoHalt — stopping the server.", PREFIX);
            server.halt(false);
        }
    }

    /** Two-decimal, locale-independent number for a check's evidence string. Locale.ROOT so a French dev box
     *  does not emit "14,07" where a scripted reader expects "14.07". */
    public static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /** Failures recorded so far for {@code suite} (0 if it never reported). Exposed for harness self-checks. */
    public static int failures(String suite) {
        return TALLIES.getOrDefault(suite, new int[2])[1];
    }

    /** Drop every tally. Only for a harness that legitimately re-runs a suite inside one server lifetime. */
    public static void reset() {
        TALLIES.clear();
    }
}
