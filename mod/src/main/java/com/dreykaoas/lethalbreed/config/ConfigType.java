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
        if (t == double[].class) return "list";
        return "float";
    }

    /** Format a double[] as a compact CSV (the storage + edit form for list options). */
    public static String csv(double[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    /** Whether {@code raw} parses for a numeric/list {@code kind} ("int"/"long"/"double"/"list") — the same
     *  acceptance {@link #parse} enforces, exposed for live edit-field validation that only has the kind label. */
    public static boolean isValidNumber(String kind, String raw) {
        try {
            String t = raw.trim();
            switch (kind) {
                case "int" -> Integer.parseInt(t);
                case "long" -> Long.parseLong(t);
                case "list" -> {
                    for (String p : t.split(",")) {
                        if (!p.isBlank()) Double.parseDouble(p.trim());
                    }
                }
                default -> Double.parseDouble(t);
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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
        if (type == double[].class) {
            String body = raw.trim();
            if (body.startsWith("[") && body.endsWith("]")) {
                body = body.substring(1, body.length() - 1);
            }
            if (body.isBlank()) return new double[0];
            String[] parts = body.split(",");
            double[] out = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                out[i] = Double.parseDouble(parts[i].trim());
            }
            return out;
        }
        throw new IllegalArgumentException("unsupported type " + type.getSimpleName());
    }
}
