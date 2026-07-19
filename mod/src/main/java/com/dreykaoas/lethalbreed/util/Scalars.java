package com.dreykaoas.lethalbreed.util;

/**
 * Tiny stateless scalar helpers shared across the hot paths (LOD distance checks, mood scoring). Kept
 * allocation-free and trivially JIT-inlinable so routing through them costs nothing at runtime.
 */
public final class Scalars {
    private Scalars() {
    }

    /** Square of a value — clearer and cheaper than {@code Math.pow(v, 2)} for squared-distance comparisons. */
    public static double sq(double v) {
        return v * v;
    }
}
