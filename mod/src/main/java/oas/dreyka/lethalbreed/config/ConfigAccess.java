package oas.dreyka.lethalbreed.config;

import oas.dreyka.lethalbreed.config.schema.ConfigSchema;
import oas.dreyka.lethalbreed.config.schema.ConfigType;

import oas.dreyka.lethalbreed.config.io.ConfigIo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Field value access for config options: read the current value, apply/parse a new one, and reset to the
 * captured defaults.
 *
 * <p>{@link #DEFAULTS} is captured at this class's init (which happens the first time any config value is
 * read or written) and therefore BEFORE the JSON load or any command can mutate a field, preserving the
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
     * icon writes back when clicked. A missed capture therefore does not fail loudly: it silently turns
     * one option's reset button into a corruption button.
     *
     * <p>Public only because {@code ConfigSchema} sits in the {@code config.schema} sub-package; it is that
     * method's private helper and has no other caller.
     */
    public static void captureDefaultsFor(Class<?> holder) {
        List<Field> fields = new ArrayList<>();
        for (Field f : holder.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isPublic(m) && Modifier.isStatic(m) && !Modifier.isFinal(m)
                    && ConfigSchema.isSupported(f.getType())) {
                fields.add(f);
            }
        }
        captureInto(DEFAULTS, fields);
    }

    private static Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        captureInto(m, ConfigSchema.all());
        return m;
    }

    /** Read every field's current value into {@code target}, keyed by field name. Shared by the initial
     *  snapshot (all schema fields) and a late holder registration (just that holder's fields): an option
     *  with no captured default resets to nothing and reports success anyway, so a broken build throws here
     *  rather than tolerating the gap. */
    private static void captureInto(Map<String, Object> target, List<Field> fields) {
        for (Field f : fields) {
            try {
                target.put(f.getName(), ConfigType.copyIfArray(f.get(null)));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("cannot capture default for " + f.getName(), e);
            }
        }
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
     *  AND the value actually changed. A GUI edit box fires one packet per keystroke, and most
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
                ConfigIo.save(); // couldn't compare, fall back to the old always-save behaviour
            }
        }
        return true;
    }

}
