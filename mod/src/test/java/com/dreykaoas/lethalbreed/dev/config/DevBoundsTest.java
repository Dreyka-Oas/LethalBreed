package com.dreykaoas.lethalbreed.dev.config;

import com.dreykaoas.lethalbreed.config.ConfigBounds;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code ConfigBoundsTest.everyScalarNumericOptionHasBounds} iterates {@code ConfigSchema.all()}, and
 * nothing registers the dev holder under JUnit, so {@code devSpawnRadius} and {@code debugLogInterval} are
 * invisible to it — deleting {@link DevBounds} outright would break no test. Both ranges were covered before
 * the dev options moved out of {@code src/main} into this source set; this is the safety net that reaches
 * them now.
 *
 * <p>Deliberately does NOT call {@code ConfigSchema.registerHolder(DevTestConfig.class)}: that mutates the
 * process-global {@code ConfigSchema.HOLDERS} list, which {@code ConfigSchemaOrderTest} pins against a
 * golden file and which {@code ConfigBoundsTest.everyScalarNumericOptionHasBounds} scans in full — and
 * undoing it needs {@code ConfigSchema#unregisterHolder}, package-private to {@code config.schema} (which is
 * exactly why {@code ConfigSchemaRegisterTest}, the pattern this class would otherwise copy, lives in that
 * package rather than here). This test only needs {@link ConfigBounds#registerGroup}, which is public,
 * never touches {@code ConfigSchema.HOLDERS} and therefore cannot corrupt either of those tests regardless
 * of JUnit's discovery order — nothing here needs an {@code @AfterEach} undo.
 *
 * <p>Registered once in {@code @BeforeAll} rather than per test: {@code ConfigBoundsTable}'s registration is
 * a plain {@code Map.put} keyed by field name, so re-registering the identical (name, min, max) triple is a
 * harmless overwrite with an equal value — but once is enough, so this doesn't lean on that.
 */
class DevBoundsTest {

    @BeforeAll
    static void registerDevBounds() {
        ConfigBounds.registerGroup(DevBounds::register);
    }

    @Test
    void devSpawnRadiusBoundsAreOneTo256() {
        assertEquals(1, ConfigBounds.clamp("devSpawnRadius", -5));
        assertEquals(256, ConfigBounds.clamp("devSpawnRadius", 99999));
        assertEquals(42, ConfigBounds.clamp("devSpawnRadius", 42));
    }

    @Test
    void debugLogIntervalBoundsAreZeroTo1_000_000() {
        assertEquals(0, ConfigBounds.clamp("debugLogInterval", -5));
        assertEquals(1_000_000, ConfigBounds.clamp("debugLogInterval", 5_000_000));
        assertEquals(100, ConfigBounds.clamp("debugLogInterval", 100));
    }
}
