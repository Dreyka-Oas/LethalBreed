package com.dreykaoas.lethalbreed.config;

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
                m.put(f.getName(), f.get(null));
            } catch (IllegalAccessException ignored) {
            }
        }
        return m;
    }

    public static String read(Field f) {
        try {
            return String.valueOf(f.get(null));
        } catch (IllegalAccessException e) {
            return "?";
        }
    }

    public static String defaultOf(String name) {
        Object d = DEFAULTS.get(name);
        return d == null ? "?" : String.valueOf(d);
    }

    /** Apply a value to a field by name. Returns true on success. Persists to JSON when {@code persist}. */
    public static boolean apply(String name, String raw, boolean persist) {
        Field f = ConfigSchema.find(name);
        if (f == null) {
            return false;
        }
        try {
            f.set(null, ConfigBounds.clamp(f.getName(), ConfigType.parse(f.getType(), raw)));
        } catch (RuntimeException | IllegalAccessException ex) {
            return false;
        }
        if (persist) {
            ConfigIo.save();
        }
        return true;
    }

    public static void reset(Field f) {
        try {
            f.set(null, DEFAULTS.get(f.getName()));
        } catch (IllegalAccessException ignored) {
        }
    }

    public static int resetAll() {
        int n = 0;
        for (Field f : ConfigSchema.all()) {
            reset(f);
            n++;
        }
        ConfigIo.save();
        return n;
    }
}
