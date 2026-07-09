package com.dreykaoas.lethalbreed.phase;

/**
 * Reads a per-phase factor table against the {@link PhaseManager#current() current phase}: a negative phase
 * (nothing active) yields {@code 0}, an in-range phase yields its entry, and a phase past the end clamps to the
 * last entry (or {@code 1.0} if the table is empty). Shared by the spawn mixins so mob-cap and frequency
 * scaling read their tables the exact same way.
 */
public final class PhaseTable {
    private PhaseTable() {
    }

    public static double sample(double[] table) {
        int phase = PhaseManager.current();
        if (phase < 0) return 0.0;
        return phase < table.length ? table[phase] : (table.length > 0 ? table[table.length - 1] : 1.0);
    }
}
