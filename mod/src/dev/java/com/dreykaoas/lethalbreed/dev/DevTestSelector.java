package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.domain.engine.DevTestConfig;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
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

    /** A suite's flag: how to read it and how to write it. Both halves are needed — {@link #apply} forces
     *  exactly one on, and {@link #enabled()} reports which are on when no selection is in force. */
    private record Flag(BooleanSupplier get, Consumer<Boolean> set) {}

    /** Suite name → its DevTestConfig flag. Insertion order drives the log listing. */
    private static final Map<String, Flag> SUITES = new LinkedHashMap<>();

    static {
        SUITES.put("special", new Flag(() -> DevTestConfig.devSpecialTest, v -> DevTestConfig.devSpecialTest = v));
        SUITES.put("mech", new Flag(() -> DevTestConfig.devMechTest, v -> DevTestConfig.devMechTest = v));
        SUITES.put("climb", new Flag(() -> DevTestConfig.devClimbTest, v -> DevTestConfig.devClimbTest = v));
        SUITES.put("compute", new Flag(() -> DevTestConfig.devComputeTest, v -> DevTestConfig.devComputeTest = v));
        SUITES.put("plague", new Flag(() -> DevTestConfig.devPlagueTest, v -> DevTestConfig.devPlagueTest = v));
        SUITES.put("statue", new Flag(() -> DevTestConfig.devStatueTest, v -> DevTestConfig.devStatueTest = v));
        SUITES.put("clear", new Flag(() -> DevTestConfig.devClearTest, v -> DevTestConfig.devClearTest = v));
        SUITES.put("placed", new Flag(() -> DevTestConfig.devPlacedTest, v -> DevTestConfig.devPlacedTest = v));
        SUITES.put("shade", new Flag(() -> DevTestConfig.devShadeTest, v -> DevTestConfig.devShadeTest = v));
        SUITES.put("breach", new Flag(() -> DevTestConfig.devBreachTest, v -> DevTestConfig.devBreachTest = v));
        // Foundation self-test: proves the synthetic-player presence every arena suite above relies on.
        SUITES.put("presence", new Flag(() -> DevTestConfig.devPresenceTest, v -> DevTestConfig.devPresenceTest = v));
        SUITES.put("pack", new Flag(() -> DevTestConfig.devPackTest, v -> DevTestConfig.devPackTest = v));
    }

    /** Every suite whose flag is currently on, in registry order. The overlap warning used to enumerate
     *  four of the eleven by hand, so e.g. statue + shade fighting over the same forced chunks went
     *  unmentioned; deriving it from the registry means a new suite can never be forgotten. */
    public static List<String> enabled() {
        List<String> on = new ArrayList<>();
        for (Map.Entry<String, Flag> e : SUITES.entrySet()) {
            if (e.getValue().get().getAsBoolean()) {
                on.add(e.getKey());
            }
        }
        return on;
    }

    /** Every registered suite name, in registry order. */
    public static Set<String> names() {
        return SUITES.keySet();
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
        Flag flag = SUITES.get(name);
        if (flag == null) {
            // Do NOT silently fall through to the config-file values: the operator asked for a specific suite
            // and would otherwise read whatever ran as if it were the suite they named.
            LethalBreed.LOGGER.error("[LB-Select] {}={} is not a known suite. Known suites: {}. "
                    + "ALL dev test flags forced OFF for this run.", src, name, String.join(", ", SUITES.keySet()));
            SUITES.values().forEach(f -> f.set().accept(false));
            return;
        }

        // Force the invariant: every flag off, then exactly the requested one back on. Order matters — clearing
        // first means a suite listed twice or a stale config value cannot leave a second arena enabled.
        SUITES.values().forEach(f -> f.set().accept(false));
        flag.set().accept(true);
        selected = name;
        LethalBreed.LOGGER.info("[LB-Select] suite '{}' selected (src={}) — exactly this dev flag is ON, the "
                + "other {} forced OFF. Arenas can no longer overlap.", name, src, SUITES.size() - 1);
    }
}
