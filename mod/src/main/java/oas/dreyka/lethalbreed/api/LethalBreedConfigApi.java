package oas.dreyka.lethalbreed.api;

import oas.dreyka.lethalbreed.config.ConfigBounds;
import oas.dreyka.lethalbreed.config.schema.ConfigCategories;
import oas.dreyka.lethalbreed.config.schema.ConfigSchema;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;

/**
 * Put your own options in the same file, the same command and the same screen as this mod's.
 *
 * <p>One call from {@link LethalBreedAddon#onLethalBreedReady()} and your holder's fields become real
 * options: written into {@code config/oas/lethalbreed.json} under their own heading, editable in game, and
 * clamped like every other. Call it later than that and the file has already been read, so the values a
 * player saved would be dropped as unknown on the next write.
 */
public final class LethalBreedConfigApi {
    private LethalBreedConfigApi() {}

    /**
     * Register a holder class whose public static non-final primitive fields are your options.
     *
     * <p>Every one of them has to start with {@code prefix}. That is what keeps two mods from naming the
     * same option and what tells the screen which tab to draw yours on, and it is checked rather than
     * trusted, because a single field that slipped the rule would land in Misc beside a stranger's.
     *
     * <p>The GUI labels a row {@code lethalbreed.option.<fieldName>} and the tab
     * {@code lethalbreed.category.<category>}, both with the raw name as fallback. Those keys go in your own
     * lang file: the game merges them across mods, so you write the {@code lethalbreed.} namespace yourself
     * without touching this mod's files.
     *
     * @param prefix the option-name prefix you claim, three characters at least
     * @param category the tab heading, both in the JSON file and in the screen sidebar
     * @param holder the class declaring your option fields, with their defaults as initialisers
     * @param bounds the range for each option; anything not listed passes through unclamped
     * @throws IllegalArgumentException if the prefix is taken, if it would capture an option that already
     *         exists, or if a field on the holder does not carry it
     */
    public static void register(String prefix, String category, Class<?> holder, OptionBounds... bounds) {
        // Both refusals happen before anything is written down, so a rejected addon leaves neither a claim
        // nor a holder behind. The claim still has to precede the holder: it asks the live schema whether
        // the prefix would capture an existing option, and a holder added ahead of it would answer with the
        // addon's own fields.
        checkFieldNames(prefix, holder);
        ConfigCategories.reserve(prefix, category);
        ConfigSchema.registerHolder(holder);
        ConfigBounds.registerGroup(r -> {
            for (OptionBounds b : bounds) {
                r.b(b.option(), b.min(), b.max());
            }
        });
    }

    private static void checkFieldNames(String prefix, Class<?> holder) {
        String key = prefix.trim().toLowerCase(Locale.ROOT);
        for (Field f : holder.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod) && Modifier.isPublic(mod) && !Modifier.isFinal(mod)
                    && ConfigSchema.isSupported(f.getType())
                    && !f.getName().toLowerCase(Locale.ROOT).startsWith(key)) {
                throw new IllegalArgumentException("option " + f.getName() + " on " + holder.getName()
                        + " does not start with the claimed prefix " + prefix);
            }
        }
    }
}
