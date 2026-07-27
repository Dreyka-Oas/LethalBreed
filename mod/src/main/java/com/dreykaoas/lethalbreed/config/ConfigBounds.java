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
     * no bounds or is a boolean.
     *
     * <p>Non-finite doubles/floats ({@code NaN}/{@code Infinity}) never get through, whether or not the
     * field is in the bounds table. When bounds exist they are pulled to the lower bound (a safe, in-range
     * value); when they do not, there is no principled in-range value to pick, so the assignment is
     * REFUSED — {@link ConfigAccess#apply} turns the exception into {@code false} and the field keeps
     * whatever valid value it already had. Silently letting a non-finite value through was the real hole:
     * some of them land in persistent per-entity attachments (contamination intensity), so a later config
     * fix cannot undo the damage.
     *
     * @throws IllegalArgumentException when the value is non-finite and the field has no registered bounds
     */
    public static Object clamp(String name, Object value) {
        Range r = ConfigBoundsTable.get(name);
        if (r == null) {
            rejectNonFinite(name, value);
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

    /**
     * Reject {@code NaN}/{@code Infinity} on a field the bounds table doesn't cover. Arrays are checked
     * element-wise for the same reason — {@code double[]} options are neither length-bounded nor
     * content-validated anywhere else.
     */
    private static void rejectNonFinite(String name, Object value) {
        if (value instanceof Double d && !Double.isFinite(d)) {
            throw new IllegalArgumentException("non-finite value for unbounded option " + name + ": " + d);
        }
        if (value instanceof Float f && !Float.isFinite(f)) {
            throw new IllegalArgumentException("non-finite value for unbounded option " + name + ": " + f);
        }
        if (value instanceof double[] arr) {
            for (double v : arr) {
                if (!Double.isFinite(v)) {
                    throw new IllegalArgumentException(
                            "non-finite element in array option " + name + ": " + v);
                }
            }
        }
    }
}
