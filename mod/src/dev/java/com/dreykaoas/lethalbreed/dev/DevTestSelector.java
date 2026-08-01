package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Picks exactly ONE dev verification suite per server run, from the environment rather than from whatever
 * the config file happens to have left enabled.
 *
 * <p><b>Why this replaces a warning.</b> The arena harnesses all build at Y=101 with overlapping columns, so
 * two enabled at once silently corrupt each other's geometry — one test's roof blocks another's {@code
 * canSeeSky}, one test's floor overwrites another's pit. The previous behaviour was to count the enabled dev
 * flags at {@code SERVER_STARTED} and log a warning, i.e. to detect the corruption and then produce corrupted
 * results anyway. Warning-and-hoping is not a guard. This forces the invariant instead: name a suite and
 * exactly that one flag is on, every other one off, no matter what the config file says.
 *
 * <p><b>Selection source</b>, in order: the {@code LB_DEV_TEST} environment variable, then the
 * {@code lethalbreed.devTest} system property. Loom's {@code runServer}/{@code runClient} are plain
 * Gradle {@code JavaExec} tasks, which inherit the Gradle daemon's environment by default, so
 * {@code LB_DEV_TEST=climb ./gradlew runServer} reaches the game with no {@code build.gradle.kts} change —
 * this is verified at boot by the {@code src=} field of the selection log line, which names whichever source
 * actually carried the value. Unset, blank, {@code off} or {@code none} means "leave the config file alone",
 * preserving the pre-existing behaviour exactly.
 */
public final class DevTestSelector {
    private DevTestSelector() {}

    public static final String ENV_VAR = "LB_DEV_TEST";
    public static final String SYS_PROP = "lethalbreed.devTest";

    /** Suite name → the setter for its ProgressionConfig flag. Insertion order drives the log listing. */
    private static final Map<String, Consumer<Boolean>> SUITES = new LinkedHashMap<>();

    static {
        SUITES.put("special", v -> ProgressionConfig.devSpecialTest = v);
        SUITES.put("mech", v -> ProgressionConfig.devMechTest = v);
        SUITES.put("climb", v -> ProgressionConfig.devClimbTest = v);
        SUITES.put("compute", v -> ProgressionConfig.devComputeTest = v);
        SUITES.put("plague", v -> ProgressionConfig.devPlagueTest = v);
        SUITES.put("statue", v -> ProgressionConfig.devStatueTest = v);
        SUITES.put("clear", v -> ProgressionConfig.devClearTest = v);
        SUITES.put("placed", v -> ProgressionConfig.devPlacedTest = v);
        SUITES.put("shade", v -> ProgressionConfig.devShadeTest = v);
        SUITES.put("breach", v -> ProgressionConfig.devBreachTest = v);
        // Foundation self-test: proves the synthetic-player presence every arena suite above relies on.
        SUITES.put("presence", v -> ProgressionConfig.devPresenceTest = v);
    }

    /** The suite selected on this run, or null when the config file's own values are in force. */
    private static String selected = null;

    /** The suite selected on this run, or {@code null} if selection was off (config-file values kept). */
    public static String selected() {
        return selected;
    }

    /**
     * Apply the selection. Must run BEFORE any harness gating decision reads its flag — i.e. before the
     * {@code SERVER_STARTED} listeners and before the first {@code END_SERVER_TICK}. Idempotent.
     */
    public static void apply() {
        String raw = System.getenv(ENV_VAR);
        String src = ENV_VAR;
        if (raw == null || raw.isBlank()) {
            raw = System.getProperty(SYS_PROP);
            src = SYS_PROP;
        }
        if (raw == null || raw.isBlank()) {
            LethalBreed.LOGGER.info("[LB-Select] no suite requested ({} / -D{} unset) — dev flags left at their "
                    + "config-file values.", ENV_VAR, SYS_PROP);
            return;
        }

        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (name.equals("off") || name.equals("none")) {
            LethalBreed.LOGGER.info("[LB-Select] {}={} — selection disabled, dev flags left at their "
                    + "config-file values.", src, name);
            return;
        }
        Consumer<Boolean> setter = SUITES.get(name);
        if (setter == null) {
            // Do NOT silently fall through to the config-file values: the operator asked for a specific suite
            // and would otherwise read whatever ran as if it were the suite they named.
            LethalBreed.LOGGER.error("[LB-Select] {}={} is not a known suite. Known suites: {}. "
                    + "ALL dev test flags forced OFF for this run.", src, name, String.join(", ", SUITES.keySet()));
            SUITES.values().forEach(s -> s.accept(false));
            return;
        }

        // Force the invariant: every flag off, then exactly the requested one back on. Order matters — clearing
        // first means a suite listed twice or a stale config value cannot leave a second arena enabled.
        SUITES.values().forEach(s -> s.accept(false));
        setter.accept(true);
        selected = name;
        LethalBreed.LOGGER.info("[LB-Select] suite '{}' selected (src={}) — exactly this dev flag is ON, the "
                + "other {} forced OFF. Arenas can no longer overlap.", name, src, SUITES.size() - 1);
    }
}
