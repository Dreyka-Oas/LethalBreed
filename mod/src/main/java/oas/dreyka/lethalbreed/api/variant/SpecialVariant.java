package oas.dreyka.lethalbreed.api.variant;

import java.util.Locale;
import java.util.function.IntSupplier;
import java.util.regex.Pattern;

/**
 * One kind of special zombie: what it is called on disk, when it starts appearing, how often, and what it
 * does. A record rather than an enum constant, so a variant can come from somewhere else.
 *
 * @param id written into the save and never translated. Namespace yours ({@code "mymod:frostbite"}); the
 *        eight shipped ones are bare words and stay that way, because saves already hold them
 * @param kind where the behaviour runs, which decides which {@link VariantBehavior} method is called
 * @param unlockPhase the phase this becomes available from, read at every roll rather than once, so a
 *        server owner moving the option in the GUI is obeyed without a restart
 * @param weight relative frequency against the other available variants; zero is never picked
 * @param behavior what it does, or null for a variant that is only a set of spawn-time stats
 */
public record SpecialVariant(String id, Kind kind, IntSupplier unlockPhase, IntSupplier weight,
                             VariantBehavior behavior) {

    /** Where the behaviour runs: at spawn only, every activation, or on death. */
    public enum Kind { PASSIVE, ACTIVE, DEATH }

    // An id reaches the save file, a lang key and a config option name, so it is held to what all three
    // accept. No upper case, because fromId matching is exact and a stray capital would read back as
    // "no variant" on a world that had one.
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]+(:[a-z0-9_/-]+)?");

    public SpecialVariant {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("a variant id looks like mymod:frostbite, got: " + id);
        }
        if (kind == null) {
            throw new IllegalArgumentException("no kind given for variant " + id);
        }
        if (unlockPhase == null || weight == null) {
            throw new IllegalArgumentException("unlockPhase and weight are read live, "
                    + "so both are needed for variant " + id);
        }
        if (behavior == null) {
            behavior = new VariantBehavior() {};
        }
    }

    /** A variant available from the first phase, at a fixed weight. The short way in for an addon. */
    public static SpecialVariant of(String id, Kind kind, int weight, VariantBehavior behavior) {
        return new SpecialVariant(id, kind, () -> 0, () -> weight, behavior);
    }

    /**
     * Key for the name shown on the entity. The colon of a namespaced id becomes a dot, because that is
     * what a lang file reads comfortably and the shipped ids, having no colon, keep the keys they had.
     */
    public String translationKey() {
        return "lethalbreed.special." + id.replace(':', '.').toLowerCase(Locale.ROOT);
    }
}
