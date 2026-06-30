package com.dreykaoas.lethalbreed.config;

import com.dreykaoas.lethalbreed.config.ConfigBoundsTable.Range;

/**
 * Sane numeric bounds for config options, applied centrally in {@link ConfigAccess#apply}. The config layer
 * is reflection-based and intentionally schema-free, but a raw {@code f.set} from the GUI, a command or a
 * hand-edited JSON would otherwise accept pathological values (negative grid sizes → {@code
 * NegativeArraySizeException}, {@code flowCpuThreads=99999} → {@code ForkJoinPool} overflow, a chance &gt; 1
 * silently breaking a roll, {@code NaN}/{@code Infinity} poisoning an attribute modifier, …).
 *
 * <p>Clamping here covers EVERY entry point (GUI packet, command, JSON load) in one place, keeps the
 * reflective {@link ConfigSchema} untouched, and is a no-op for every in-range value — so default configs
 * and sane edits behave exactly as before. Booleans and unlisted fields pass through unchanged.
 *
 * <p>The bound ranges themselves live in {@link ConfigBoundsTable}; this class holds only the clamp logic.
 */
public final class ConfigBounds {
    private ConfigBounds() {}

    /**
     * Clamp a freshly parsed value to its registered bounds. Returns the value unchanged when the field has
     * no bounds or is a boolean. Non-finite doubles/floats ({@code NaN}/{@code Infinity}) are pulled to the
     * lower bound (a safe, in-range value) rather than allowed to poison downstream math.
     */
    public static Object clamp(String name, Object value) {
        Range r = ConfigBoundsTable.get(name);
        if (r == null) {
            return value;
        }
        if (value instanceof Integer i) {
            return (int) Math.max(r.min(), Math.min((double) i, r.max()));
        }
        if (value instanceof Long l) {
            return (long) Math.max(r.min(), Math.min((double) l, r.max()));
        }
        if (value instanceof Float f) {
            if (!Float.isFinite(f)) {
                return (float) r.min();
            }
            return (float) Math.max(r.min(), Math.min((double) f, r.max()));
        }
        if (value instanceof Double d) {
            if (!Double.isFinite(d)) {
                return r.min();
            }
            return Math.max(r.min(), Math.min(d, r.max()));
        }
        return value;
    }
}
