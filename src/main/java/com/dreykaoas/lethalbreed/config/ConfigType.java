package com.dreykaoas.lethalbreed.config;

import java.lang.reflect.Field;

/**
 * Primitive-type handling for config fields: the short {@code kind} label shown in the UI/commands, and
 * parsing a raw string into the field's primitive type.
 */
public final class ConfigType {
    private ConfigType() {}

    public static String kind(Field f) {
        Class<?> t = f.getType();
        if (t == boolean.class) return "bool";
        if (t == int.class) return "int";
        if (t == long.class) return "long";
        if (t == double.class) return "double";
        return "float";
    }

    /** Parse a string into the field's primitive type. Throws on malformed input. */
    public static Object parse(Class<?> type, String raw) {
        if (type == boolean.class) {
            if (raw.equalsIgnoreCase("true") || raw.equals("1")) return Boolean.TRUE;
            if (raw.equalsIgnoreCase("false") || raw.equals("0")) return Boolean.FALSE;
            throw new IllegalArgumentException("expected true/false");
        }
        if (type == int.class) return Integer.parseInt(raw.trim());
        if (type == long.class) return Long.parseLong(raw.trim());
        if (type == double.class) return Double.parseDouble(raw.trim());
        if (type == float.class) return Float.parseFloat(raw.trim());
        throw new IllegalArgumentException("unsupported type " + type.getSimpleName());
    }
}
