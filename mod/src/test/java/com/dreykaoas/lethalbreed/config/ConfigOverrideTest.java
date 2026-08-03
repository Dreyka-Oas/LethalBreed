package com.dreykaoas.lethalbreed.config;

import com.dreykaoas.lethalbreed.config.schema.ConfigSchema;
import com.dreykaoas.lethalbreed.config.schema.ConfigType;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.SchedulerConfig;
import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Headless characterization of the scoped config override. Touches only {@code config.domain} holders, which
 * have no Minecraft or Fabric imports, so nothing here can drag a runtime class into the test JVM.
 */
class ConfigOverrideTest {

    @Test
    void restoresScalarOnClose() {
        int before = SchedulerConfig.tickBuckets;
        try (ConfigOverride cfg = ConfigOverride.open()) {
            cfg.set("tickBuckets", before + 7);
            assertEquals(before + 7, SchedulerConfig.tickBuckets);
        }
        assertEquals(before, SchedulerConfig.tickBuckets);
    }

    @Test
    void restoresEveryTouchedFieldOnClose() {
        int retryBefore = ZombieMoodConfig.shelterRetryTicks;
        boolean shelterBefore = ZombieMoodConfig.sunShelterEnabled;
        try (ConfigOverride cfg = ConfigOverride.open()) {
            cfg.set("shelterRetryTicks", 999).set("sunShelterEnabled", !shelterBefore);
        }
        assertEquals(retryBefore, ZombieMoodConfig.shelterRetryTicks);
        assertEquals(shelterBefore, ZombieMoodConfig.sunShelterEnabled);
    }

    @Test
    void firstValueWinsAsTheRestorePoint() {
        int before = SchedulerConfig.tickBuckets;
        try (ConfigOverride cfg = ConfigOverride.open()) {
            cfg.set("tickBuckets", before + 1);
            cfg.set("tickBuckets", before + 2);
        }
        assertEquals(before, SchedulerConfig.tickBuckets);
    }

    @Test
    void optionNameIsCaseInsensitiveLikeTheRestOfTheConfigLayer() {
        int before = SchedulerConfig.tickBuckets;
        try (ConfigOverride cfg = ConfigOverride.open()) {
            cfg.set("TICKBUCKETS", before + 4);
            assertEquals(before + 4, SchedulerConfig.tickBuckets);
        }
        assertEquals(before, SchedulerConfig.tickBuckets);
    }

    @Test
    void unknownOptionFailsLoud() {
        try (ConfigOverride cfg = ConfigOverride.open()) {
            IllegalArgumentException boom =
                    assertThrows(IllegalArgumentException.class, () -> cfg.set("noSuchOption", 1));
            assertTrue(boom.getMessage().contains("noSuchOption"));
        }
    }

    /** An array option must be restored by VALUE. If the restore point held the live array by reference, a
     *  scope that mutated it in place would restore the mutation and the "original" would be gone. Resolved
     *  by type rather than by name so a later holder split cannot silently skip this case. */
    @Test
    void arrayOptionIsRestoredByValueNotByReference() {
        Field arrayOption = firstArrayOption();
        assumeTrue(arrayOption != null, "schema declares no double[] option");

        double[] original = (double[]) read(arrayOption);
        try (ConfigOverride cfg = ConfigOverride.open()) {
            cfg.set(arrayOption.getName(), new double[] { 42.0 });
            assertArrayEquals(new double[] { 42.0 }, (double[]) read(arrayOption));
        }
        assertArrayEquals(original, (double[]) read(arrayOption));
    }

    private static Field firstArrayOption() {
        for (Field f : ConfigSchema.all()) {
            if (f.getType() == double[].class) {
                return f;
            }
        }
        return null;
    }

    private static Object read(Field f) {
        try {
            return f.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("config option " + f.getName() + " is not readable", e);
        }
    }

    @Test
    void closeIsIdempotent() {
        int before = SchedulerConfig.tickBuckets;
        ConfigOverride cfg = ConfigOverride.open();
        cfg.set("tickBuckets", before + 3);
        cfg.close();
        SchedulerConfig.tickBuckets = before + 99;   // something else moved it afterwards
        cfg.close();                                 // must NOT stomp that
        assertEquals(before + 99, SchedulerConfig.tickBuckets);
        SchedulerConfig.tickBuckets = before;
    }

    // ---- Type agreement between the caller's box and the field ----------------------------------
    // ConfigAccess.apply never hits a type mismatch because it derives the value from f.getType() via
    // ConfigType.parse. set() took the caller's box and wrote it raw, so a Double into one of the seven
    // float options threw a raw IllegalArgumentException out of Field.set — past the IllegalAccessException
    // catch, and past the caller's own scope handling.

    @Test
    void coercesADoubleIntoAFloatOption() {
        float before = ZombieMoodConfig.screamVolume;
        try (ConfigOverride cfg = ConfigOverride.open()) {
            cfg.set("screamVolume", 2.5);          // Double literal, float field
            assertEquals(2.5f, ZombieMoodConfig.screamVolume);
        }
        assertEquals(before, ZombieMoodConfig.screamVolume);
    }

    @Test
    void coercesAnIntegerIntoALongOption() {
        long before = CombatMoveConfig.breakGraceTicks;
        try (ConfigOverride cfg = ConfigOverride.open()) {
            cfg.set("breakGraceTicks", 5);         // Integer literal, long field
            assertEquals(5L, CombatMoveConfig.breakGraceTicks);
        }
        assertEquals(before, CombatMoveConfig.breakGraceTicks);
    }

    @Test
    void coercesAnIntegerIntoADoubleOption() {
        double before = ZombieMoodConfig.fleeSpeed;
        try (ConfigOverride cfg = ConfigOverride.open()) {
            cfg.set("fleeSpeed", 2);
            assertEquals(2.0, ZombieMoodConfig.fleeSpeed);
        }
        assertEquals(before, ZombieMoodConfig.fleeSpeed);
    }

    @Test
    void rejectsAValueThatCannotBecomeTheFieldType() {
        int before = SchedulerConfig.tickBuckets;
        try (ConfigOverride cfg = ConfigOverride.open()) {
            IllegalArgumentException boom = assertThrows(IllegalArgumentException.class,
                    () -> cfg.set("tickBuckets", "not a number"));
            assertTrue(boom.getMessage().contains("tickBuckets"), boom.getMessage());
        }
        assertEquals(before, SchedulerConfig.tickBuckets);
    }

    @Test
    void rejectsABooleanForANumericOption() {
        try (ConfigOverride cfg = ConfigOverride.open()) {
            assertThrows(IllegalArgumentException.class, () -> cfg.set("tickBuckets", true));
        }
    }

    /** The invariant a supervisor depends on: a set() that throws must leave every EARLIER option in this
     *  scope restorable, so the caller can still close() on the way out. */
    @Test
    void aFailedSetLeavesEveryEarlierOptionRestorable() {
        int buckets = SchedulerConfig.tickBuckets;
        float volume = ZombieMoodConfig.screamVolume;
        ConfigOverride cfg = ConfigOverride.open();
        cfg.set("tickBuckets", buckets + 5);
        assertThrows(IllegalArgumentException.class, () -> cfg.set("screamVolume", "nope"));
        cfg.close();
        assertEquals(buckets, SchedulerConfig.tickBuckets);
        assertEquals(volume, ZombieMoodConfig.screamVolume);
    }
}
