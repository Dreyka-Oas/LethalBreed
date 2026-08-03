package com.dreykaoas.lethalbreed.config;

import com.dreykaoas.lethalbreed.config.schema.ConfigSchema;
import com.dreykaoas.lethalbreed.config.schema.ConfigType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A scoped, self-restoring change to config options.
 *
 * <p>Config lives in {@code public static} fields on the {@code config.domain} holders, so anything that
 * changes one changes it for the whole process until something changes it back. Every dev harness grew its
 * own {@code savedX} field plus a hand-written restore, and a unit test that set an option leaked it into
 * every test that ran after it. This is the one place that pattern lives:
 *
 * <pre>
 * try (ConfigOverride cfg = ConfigOverride.open()) {
 *     cfg.set("shelterRetryTicks", 100).set("sunShelterEnabled", true);
 *     ...
 * }   // every touched option is back to what it held on first touch
 * </pre>
 *
 * <p>The restore point is the value at FIRST touch, so setting the same option twice inside one scope still
 * restores the original. {@link #close()} is idempotent: a second call restores nothing, so a
 * try-with-resources that also closes explicitly cannot stomp a later, unrelated write.
 *
 * <p>Values bypass {@link ConfigBounds} deliberately — a harness that wants an out-of-range value in order to
 * prove a clamp is exactly the caller this exists for. Use {@link ConfigAccess#apply} when clamping IS wanted.
 *
 * <p>Not thread-safe, and not meant to be: config is written from the server thread and from tests, never
 * concurrently.
 */
public final class ConfigOverride implements AutoCloseable {

    /** Option → value at first touch, in first-touch order. */
    private final Map<Field, Object> saved = new LinkedHashMap<>();

    private ConfigOverride() {}

    public static ConfigOverride open() {
        return new ConfigOverride();
    }

    /**
     * Set one option by name, remembering its current value the first time this scope touches it. Name
     * matching is case-insensitive, like {@link ConfigSchema#find}.
     *
     * @throws IllegalArgumentException if {@code name} is not a known option — a typo'd option name would
     *         otherwise be a silent no-op, which is how a harness ends up measuring the wrong thing and
     *         reporting PASS for it.
     */
    public ConfigOverride set(String name, Object value) {
        Field f = ConfigSchema.find(name);
        if (f == null) {
            throw new IllegalArgumentException("no such config option: " + name);
        }
        try {
            if (!saved.containsKey(f)) {
                saved.put(f, ConfigType.copyIfArray(f.get(null)));
            }
            f.set(null, coerce(f.getType(), value));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("config option not writable: " + name, e);
        }
        return this;
    }

    /**
     * Reflection WIDENS a boxed number on its own — an {@code Integer} lands in a {@code long} or
     * {@code double} option without help — but it never NARROWS. Seven options are {@code float}, and a plain
     * decimal literal boxes to {@code Double}, so {@code set("screamVolume", 2.5)} was rejected outright.
     *
     * <p>Only that one conversion is performed. Every other mismatch (a {@code String} or a {@code boolean}
     * into a numeric option, a {@code Double} into an {@code int}) still reaches {@code Field.set} and throws
     * — its own message already names the field and both types, and silently truncating {@code 2.7} to
     * {@code 2} would be far worse than refusing it.
     */
    private static Object coerce(Class<?> type, Object value) {
        if (type == float.class && value instanceof Number n && !(value instanceof Float)) {
            return n.floatValue();
        }
        return value;
    }

    /** Restore every option this scope touched, most-recently-touched first. Idempotent. */
    @Override
    public void close() {
        List<Map.Entry<Field, Object>> entries = new ArrayList<>(saved.entrySet());
        saved.clear();
        for (int i = entries.size() - 1; i >= 0; i--) {
            Field f = entries.get(i).getKey();
            try {
                f.set(null, ConfigType.copyIfArray(entries.get(i).getValue()));
            } catch (IllegalAccessException e) {
                // Never swallow this: a failed restore leaves the process holding a test's value, and every
                // measurement taken afterwards is quietly wrong.
                throw new IllegalStateException("could not restore config option " + f.getName(), e);
            }
        }
    }
}
