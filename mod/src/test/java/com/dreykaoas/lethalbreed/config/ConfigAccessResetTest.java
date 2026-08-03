package com.dreykaoas.lethalbreed.config;

import com.dreykaoas.lethalbreed.config.io.ConfigIo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Headless characterization of the reset path. Uses {@link ConfigAccess#resetAllInMemory()} rather than
 * {@code resetAll()}: the latter persists, which reaches {@code ConfigIo} → {@code FabricLoader} and cannot
 * run without a Minecraft runtime.
 */
class ConfigAccessResetTest {

    /** resetAll() must report what it actually restored — the count is shown to the operator by
     *  {@code /lethalconfig resetall}, and an inflated number is a lie about the state of their config. */
    @Test
    void resetAllCountsOnlyRestoredOptions() {
        int options = ConfigSchema.all().size();
        assertEquals(options, ConfigAccess.resetAllInMemory());
    }

    /** A reset must actually put the default back, not merely be counted. */
    @Test
    void resetRestoresTheCapturedDefault() {
        var field = ConfigSchema.find("tickBuckets");
        String original = ConfigAccess.defaultOf("tickBuckets");
        try (ConfigOverride cfg = ConfigOverride.open()) {
            cfg.set("tickBuckets", 999);
            ConfigAccess.reset(field);
            assertEquals(original, ConfigAccess.read(field));
        }
    }
}
