package oas.dreyka.lethalbreed.api;

/**
 * The range one of your options is held to, inclusive at both ends.
 *
 * <p>A config value can arrive from a hand-edited JSON file, a command or the GUI, and every one of those
 * roads goes through the same clamp. An option with no bound still refuses NaN and infinity, but takes any
 * finite number given to it, which is how a negative grid size or a chance above one gets in.
 *
 * @param option the field name exactly as declared on your holder class, matched case-insensitively
 */
public record OptionBounds(String option, double min, double max) {}
