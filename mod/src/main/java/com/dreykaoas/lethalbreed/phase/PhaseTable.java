package com.dreykaoas.lethalbreed.phase;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

/**
 * Formula-driven mob-cap / spawn-frequency multipliers against the {@link PhaseManager#current() current
 * phase}: {@code growth * phase^exponent}, bent onto a ceiling by {@link PhaseConfig#softCap}. Shared by the
 * spawn mixins so both curves are read the exact same way.
 *
 * <p>These two used to be unbounded "by design", on the reasoning that night-spawn pressure should keep
 * rising forever. In practice that is also a performance cliff nobody chose: at phase 100 the raw mob-cap
 * multiplier is 144x vanilla, and this mod never despawns anything. The ceiling applies from
 * {@link PhaseConfig#KNEE_PHASE} upward, so the phases anyone has actually played are untouched.
 */
public final class PhaseTable {
    private PhaseTable() {
    }

    /** {@code growth * phase^exponent} — the raw curve, before any ceiling. */
    private static double raw(double growth, double exponent, int phase) {
        return growth * Math.pow(Math.max(0, phase), exponent);
    }

    public static double mobcap(int phase) {
        return PhaseConfig.softCap(
                raw(ProgressionConfig.phaseMobcapGrowth, ProgressionConfig.phaseMobcapExponent, phase),
                raw(ProgressionConfig.phaseMobcapGrowth, ProgressionConfig.phaseMobcapExponent,
                        PhaseConfig.KNEE_PHASE),
                ProgressionConfig.phaseMobcapCeiling);
    }

    public static double frequency(int phase) {
        return PhaseConfig.softCap(
                raw(ProgressionConfig.phaseFrequencyGrowth, ProgressionConfig.phaseFrequencyExponent, phase),
                raw(ProgressionConfig.phaseFrequencyGrowth, ProgressionConfig.phaseFrequencyExponent,
                        PhaseConfig.KNEE_PHASE),
                ProgressionConfig.phaseFrequencyCeiling);
    }
}
