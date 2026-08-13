package com.dreykaoas.lethalbreed.config;

import com.dreykaoas.lethalbreed.config.schema.ConfigSchema;
import com.dreykaoas.lethalbreed.config.schema.ConfigType;

import com.dreykaoas.lethalbreed.config.io.ConfigIo;

import com.dreykaoas.lethalbreed.LethalBreed;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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

    /** Default snapshot captured at class init, BEFORE the JSON load or any command can mutate fields.
     *  Only the reference is final: {@link #captureDefaultsFor} adds entries for a holder that joined the
     *  schema after this class was initialised. */
    private static final Map<String, Object> DEFAULTS = snapshot();

    /**
     * Capture defaults for a holder registered after class-init, called from
     * {@link ConfigSchema#registerHolder}.
     *
     * <p>Not optional. {@link #defaultOf} reports {@code "?"} for an option with no captured entry, and
     * that value travels: it reaches the GUI tooltip and the wire snapshot, and it is what the row's reset
     * icon writes back when clicked. A missed capture therefore does not fail loudly — it silently turns
     * one option's reset button into a corruption button.
     *
     * <p>Public only because {@code ConfigSchema} sits in the {@code config.schema} sub-package; it is that
     * method's private helper and has no other caller.
     */
    public static void captureDefaultsFor(Class<?> holder) {
        for (Field f : holder.getDeclaredFields()) {
            int m = f.getModifiers();
            if (!Modifier.isPublic(m) || !Modifier.isStatic(m) || Modifier.isFinal(m)
                    || !ConfigSchema.isSupported(f.getType())) {
                continue;
            }
            try {
                DEFAULTS.put(f.getName(), ConfigType.copyIfArray(f.get(null)));
            } catch (IllegalAccessException e) {
                // Same reasoning as snapshot(): an option with no factory default resets to nothing and
                // reports success anyway. A broken build, not a runtime condition to tolerate.
                throw new IllegalStateException("cannot capture default for " + f.getName(), e);
            }
        }
    }

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

}
