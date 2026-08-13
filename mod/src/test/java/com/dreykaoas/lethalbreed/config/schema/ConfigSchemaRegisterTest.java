package com.dreykaoas.lethalbreed.config.schema;

import com.dreykaoas.lethalbreed.config.ConfigAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A dev-side holder is registered at runtime, after class-init. Two things must survive that:
 * the option must appear in all(), and reset() must not blow up on a field whose default was
 * captured late — that path does Field.set(null, DEFAULTS.get(name)) and a missing entry is a
 * null unboxed into a primitive, i.e. IllegalArgumentException, which nothing catches.
 */
class ConfigSchemaRegisterTest {

    /** Stand-in for a dev holder — deliberately not a real one, so the test owns its lifecycle. */
    public static final class LateHolder {
        private LateHolder() {}
        public static boolean lateFlag = false;
        public static int lateCount = 7;
    }

    /**
     * Registration mutates process-global schema state, so it MUST be undone: {@code ConfigSchemaOrderTest}
     * asserts the whole option list against a golden file and {@code ConfigBoundsTest} demands a bound for
     * every numeric option — both would fail on {@code lateCount} if this holder outlived the test.
     */
    @AfterEach
    void unregister() {
        ConfigSchema.unregisterHolder(LateHolder.class);
    }

    @Test
    void aLateRegisteredHolderAppearsInAll() {
        int before = ConfigSchema.all().size();
        ConfigSchema.registerHolder(LateHolder.class);
        List<Field> after = ConfigSchema.all();

        assertEquals(before + 2, after.size());
        assertTrue(after.stream().anyMatch(f -> f.getName().equals("lateFlag")));
        assertTrue(after.stream().anyMatch(f -> f.getName().equals("lateCount")));
    }

    @Test
    void aLateRegisteredFieldGetsItsDefaultCaptured() {
        // registerHolder must capture defaults for the holder it adds. Without that, defaultOf answers "?"
        // for these fields, the GUI ships "?" as their default, and the row's reset icon writes it back.
        ConfigSchema.registerHolder(LateHolder.class);
        LateHolder.lateCount = 999;

        assertEquals("7", ConfigAccess.defaultOf("lateCount"),
                "the late-registered field's factory default must have been captured");
    }

    /** Registering twice must not duplicate the option — a duplicate would be written to the JSON twice. */
    @Test
    void registeringTwiceIsANoOp() {
        int before = ConfigSchema.all().size();
        ConfigSchema.registerHolder(LateHolder.class);
        ConfigSchema.registerHolder(LateHolder.class);

        assertEquals(before + 2, ConfigSchema.all().size());
    }

    /**
     * The cleanup this class relies on actually works. Without it a registration leaks into every test that
     * runs afterwards in the same JVM — and JUnit's class order is not something this test can control — so
     * {@code ConfigSchemaOrderTest} (whole-list assert against the golden file) and
     * {@code ConfigBoundsTest.everyScalarNumericOptionHasBounds} ({@code lateCount} is an unbounded int)
     * would fail depending on discovery order. Proving the undo here is what makes that impossible.
     */
    @Test
    void unregisteringRestoresTheShippedSchema() {
        List<Field> shipped = ConfigSchema.all();
        ConfigSchema.registerHolder(LateHolder.class);
        ConfigSchema.unregisterHolder(LateHolder.class);

        assertEquals(shipped, ConfigSchema.all());
        assertTrue(ConfigSchema.all().stream().noneMatch(f -> f.getName().startsWith("late")));
    }

    /**
     * The point of the whole exercise, stated as an assertion: no SHIPPED option carries the "Dev" category.
     *
     * <p>Nothing registers a dev holder under JUnit, so {@link ConfigSchema#all()} here is exactly the
     * schema a player's game has. {@code CustomConfigScreen} builds its sidebar from the categories the rows
     * it receives actually carry, and {@code ConfigWriter} groups the JSON the same way — so zero Dev rows
     * means no "Dev" tab and no {@code "Dev": {…}} block, with no client-side change at all. A dev/debug-named
     * option accidentally added back to {@code src/main} would reinstate both, and this fails first.
     *
     * <p>{@code contamDevTimeScale} is the deliberate near-miss: it starts with "contam", not "dev", so it
     * stays a normal Contamination option. It is a real player option and must not trip this.
     */
    @Test
    void noShippedOptionLandsInTheDevTab() {
        List<String> inDevTab = ConfigSchema.all().stream()
                .map(Field::getName)
                .filter(n -> ConfigCategory.of(n).equals("Dev"))
                .toList();

        assertTrue(inDevTab.isEmpty(),
                "these options ship in the player jar and would put a Dev tab back in the config GUI: "
                        + inDevTab);
    }
}
