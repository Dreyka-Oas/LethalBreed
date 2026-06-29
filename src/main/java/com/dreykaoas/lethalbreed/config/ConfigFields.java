package com.dreykaoas.lethalbreed.config;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Shared reflection layer over the config schema. One source of truth used by the chat command
 * ({@code /lethalconfig}), the JSON loader ({@link ConfigIo}) and the in-game GUI: every public, static,
 * non-final field is exposed automatically, so a new config option needs no per-feature wiring.
 *
 * <p>This is a thin facade — each concern lives in its own leaf class: {@link ConfigSchema} (which fields
 * exist), {@link ConfigType} (primitive kind/parse), {@link ConfigCategory} (GUI tab grouping) and
 * {@link ConfigAccess} (read/apply/reset/defaults). {@link #encodeSnapshot()} composes them into the GUI
 * wire line.
 *
 * <p>{@link #encodeSnapshot()} uses control char {@code \\u0001} as field separator and {@code \\n} as
 * line separator — neither ever appears in a field name or a numeric/boolean value, so the wire format
 * stays unambiguous.
 */
public final class ConfigFields {
    private ConfigFields() {}

    public static final char SEP = (char) 1;

    public static List<Field> all() {
        return ConfigSchema.all();
    }

    public static Field find(String name) {
        return ConfigSchema.find(name);
    }

    public static boolean isSupported(Class<?> t) {
        return ConfigSchema.isSupported(t);
    }

    public static String kind(Field f) {
        return ConfigType.kind(f);
    }

    /** Parse a string into the field's primitive type. Throws on malformed input. */
    public static Object parse(Class<?> type, String raw) {
        return ConfigType.parse(type, raw);
    }

    /** Group an option into a tab category by name (first match wins; ordered specific→generic). */
    public static String category(String name) {
        return ConfigCategory.of(name);
    }

    public static String read(Field f) {
        return ConfigAccess.read(f);
    }

    public static String defaultOf(String name) {
        return ConfigAccess.defaultOf(name);
    }

    /** Apply a value to a field by name. Returns true on success. Persists to JSON when {@code persist}. */
    public static boolean apply(String name, String raw, boolean persist) {
        return ConfigAccess.apply(name, raw, persist);
    }

    public static void reset(Field f) {
        ConfigAccess.reset(f);
    }

    public static int resetAll() {
        return ConfigAccess.resetAll();
    }

    /** Encode every option as {@code name<SEP>kind<SEP>value<SEP>default<SEP>category}, one per line, for the GUI. */
    public static String encodeSnapshot() {
        StringBuilder sb = new StringBuilder();
        for (Field f : all()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(f.getName()).append(SEP).append(kind(f)).append(SEP)
              .append(read(f)).append(SEP).append(defaultOf(f.getName()))
              .append(SEP).append(category(f.getName()));
        }
        return sb.toString();
    }
}
