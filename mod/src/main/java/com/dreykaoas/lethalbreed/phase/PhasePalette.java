package com.dreykaoas.lethalbreed.phase;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

import net.minecraft.ChatFormatting;

/**
 * The colour a phase number is shown in, in chat and in the command output.
 *
 * <p>Only the tier THRESHOLDS ({@link ProgressionConfig#phaseColorThresholds}) are configurable; the
 * palette itself is fixed, and it cycles, so a phase past the last tier keeps getting a colour instead of
 * running out of them.
 */
public final class PhasePalette {
    private PhasePalette() {}

    /** Cyclic color palette for the phase broadcast/command text — only the tier THRESHOLDS
     *  ({@link ProgressionConfig#phaseColorThresholds}) are configurable, this list is fixed. */
    private static final ChatFormatting[] COLOR_PALETTE = {
            ChatFormatting.GRAY, ChatFormatting.GREEN, ChatFormatting.YELLOW, ChatFormatting.GOLD,
            ChatFormatting.RED, ChatFormatting.DARK_RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.DARK_PURPLE,
    };

    /** Color for a given phase, per the configured tier thresholds (largest threshold <= phase wins). */
    public static ChatFormatting colorFor(int phase) {
        double[] thresholds = ProgressionConfig.phaseColorThresholds;
        int tier = 0;
        for (int i = 0; i < thresholds.length; i++) {
            if (phase >= thresholds[i]) {
                tier = i;
            }
        }
        return COLOR_PALETTE[tier % COLOR_PALETTE.length];
    }

}
