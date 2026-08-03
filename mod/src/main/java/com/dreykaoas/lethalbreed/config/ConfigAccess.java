package com.dreykaoas.lethalbreed.config;

import com.dreykaoas.lethalbreed.config.schema.ConfigSchema;
import com.dreykaoas.lethalbreed.config.schema.ConfigType;

import com.dreykaoas.lethalbreed.config.io.ConfigIo;

import com.dreykaoas.lethalbreed.LethalBreed;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Field value access for config options: read the current value, apply/parse a new one, and reset to the
 * captured defaults.
 *
 * <p>{@link #DEFAULTS} is captured at this class's init — which happens the first time any config value is
 * read or written — and therefore BEFORE the JSON load or any command can mutate a field, preserving the
 * original "factory default" snapshot semantics.
 */
public final class ConfigAccess {
    private ConfigAccess() {}

    /** Default snapshot captured at class init, BEFORE the JSON load or any command can mutate fields. */
    private static final Map<String, Object> DEFAULTS = snapshot();

    private static Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Field f : ConfigSchema.all()) {
            try {
                m.put(f.getName(), ConfigType.copyIfArray(f.get(null)));
            } catch (IllegalAccessException e) {
                // An option the schema lists but cannot read is a broken build, not a runtime condition to
                // tolerate: skipping it silently leaves that option with NO factory default, so a later
                // reset restores nothing and reports success anyway.
                throw new IllegalStateException(
                        "config option " + f.getName() + " is listed by the schema but not readable", e);
            }
        }
        return m;
    }

    public static String read(Field f) {
        try {
            Object v = f.get(null);
            return v instanceof double[] arr ? ConfigType.csv(arr) : String.valueOf(v);
        } catch (IllegalAccessException e) {
            return "?";
        }
    }

    public static String defaultOf(String name) {
        Object d = DEFAULTS.get(name);
        if (d == null) return "?";
        return d instanceof double[] arr ? ConfigType.csv(arr) : String.valueOf(d);
    }

    /** Apply a value to a field by name. Returns true on success. Persists to JSON when {@code persist}
     *  AND the value actually changed — a GUI edit box fires one packet per keystroke (audit #16), and most
     *  carry a value equal to the current one (mid-typing, or re-applying the same number), so skipping the
     *  unchanged writes removes the bulk of the redundant full-file saves without any behaviour change. */
    public static boolean apply(String name, String raw, boolean persist) {
        Field f = ConfigSchema.find(name);
        if (f == null) {
            return false;
        }
        Object before;
        try {
            before = f.get(null);
            f.set(null, ConfigBounds.clamp(f.getName(), ConfigType.parse(f.getType(), raw)));
        } catch (RuntimeException | IllegalAccessException ex) {
            return false;
        }
        if (persist) {
            try {
                if (!java.util.Objects.deepEquals(before, f.get(null))) { // deepEquals covers double[]
                    ConfigIo.save();
                }
            } catch (IllegalAccessException ex) {
                ConfigIo.save(); // couldn't compare — fall back to the old always-save behaviour
            }
        }
        return true;
    }

    /** Restore one option to its captured default. Returns true when the field was actually written. */
    public static boolean reset(Field f) {
        try {
            f.set(null, ConfigType.copyIfArray(DEFAULTS.get(f.getName())));
            return true;
        } catch (IllegalAccessException e) {
            // Counting a reset that did not happen makes /lethalconfig resetall report a config state the
            // operator does not have. Say so, and let the caller count only what landed.
            LethalBreed.LOGGER.error("[LethalBreed] could not reset config option {}", f.getName(), e);
            return false;
        }
    }

    /** Restore every option to its default WITHOUT persisting. Returns the number actually restored.
     *  Split out of {@link #resetAll()} so the reset path is reachable from a headless test — {@code save()}
     *  reaches {@code FabricLoader}, which does not exist in the test source set. */
    public static int resetAllInMemory() {
        int n = 0;
        for (Field f : ConfigSchema.all()) {
            if (reset(f)) {
                n++;
            }
        }
        return n;
    }

    /** Restore every option to its default and persist. Returns the number actually restored. */
    public static int resetAll() {
        int n = resetAllInMemory();
        ConfigIo.save();
        return n;
    }
}
