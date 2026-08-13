package com.dreykaoas.lethalbreed.config;

import com.dreykaoas.lethalbreed.config.schema.ConfigSchema;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Every option must have a captured factory default.
 *
 * <p>This used to test {@code ConfigAccess.resetAll()}. That method, its in-memory split and the
 * single-field {@code reset()} were all removed as dead: the GUI's per-row reset icon never called them —
 * it resolves the default client-side from the wire snapshot and sends the value back through the
 * {@code SetConfig} packet — and no other caller existed once {@code /lethalconfig reset} was dropped.
 *
 * <p>The invariant underneath survives the deletion and matters more than the method did.
 * {@link ConfigAccess#defaultOf} answers {@code "?"} for an option whose default was never captured, and
 * that answer is not inert: it is what {@code encodeSnapshot} ships to the GUI and therefore what the reset
 * icon writes back when a player clicks it. A holder registered without
 * {@link ConfigAccess#captureDefaultsFor} thus turns one row's reset button into a corruption button, with
 * no exception anywhere to notice it. This is the test that would.
 */
class ConfigDefaultsTest {

    @Test
    void everyOptionHasACapturedDefault() {
        for (Field f : ConfigSchema.all()) {
            String def = ConfigAccess.defaultOf(f.getName());
            assertNotNull(def, f.getName() + " has no captured default");
            assertNotEquals("?", def,
                    f.getName() + " has no captured default — its reset icon would write \"?\"");
        }
    }
}
