package com.dreykaoas.lethalbreed.config.schema;

/**
 * The 6 primitive kinds a config field can be. Single source of truth for "what kind of field is this",
 * used by {@link ConfigType#kind}, {@link ConfigSchema#isSupported}, and {@code ConfigWriter}'s per-field
 * JSON write dispatch — three places that used to independently re-derive this same 6-way type check.
 */
public enum ConfigPrimitive {
    BOOL("bool"), INT("int"), LONG("long"), DOUBLE("double"), FLOAT("float"), LIST("list");

    private final String label;

    ConfigPrimitive(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Matches a field's declared {@link Class}. Returns {@code null} for anything else — callers decide
     *  their own fallback (historically {@link ConfigType#kind} falls back to {@code "float"}'s label,
     *  while {@link ConfigSchema#isSupported} must treat {@code null} as "not supported"). */
    public static ConfigPrimitive of(Class<?> t) {
        if (t == boolean.class) return BOOL;
        if (t == int.class) return INT;
        if (t == long.class) return LONG;
        if (t == double.class) return DOUBLE;
        if (t == float.class) return FLOAT;
        if (t == double[].class) return LIST;
        return null;
    }
}
