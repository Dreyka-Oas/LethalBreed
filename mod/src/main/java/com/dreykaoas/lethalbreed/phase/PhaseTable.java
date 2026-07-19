package com.dreykaoas.lethalbreed.phase;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

/**
 * Formula-driven mob-cap / spawn-frequency multipliers against the {@link PhaseManager#current() current
 * phase}: {@code growth * phase^exponent}, unbounded above (no ceiling, by design — the night-spawn
 * pressure keeps rising forever with the phase). Shared by the spawn mixins so both curves are read the
 * exact same way.
 */
public final class PhaseTable {
    private PhaseTable() {
    }

    public static double mobcap(int phase) {
        int p = Math.max(0, phase);
        return ProgressionConfig.phaseMobcapGrowth * Math.pow(p, ProgressionConfig.phaseMobcapExponent);
    }

    public static double frequency(int phase) {
        int p = Math.max(0, phase);
        return ProgressionConfig.phaseFrequencyGrowth * Math.pow(p, ProgressionConfig.phaseFrequencyExponent);
    }
}
