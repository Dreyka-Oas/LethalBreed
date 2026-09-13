package oas.dreyka.lethalbreed.config.schema;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Option-name prefixes another mod has claimed, and the GUI tab each one routes to.
 *
 * <p>{@link ConfigCategory} is a chain of keyword rules written against this mod's own option names, and
 * those keywords do not stop at the mod's border: an addon shipping {@code packedIceMeltChance} would be
 * read as a Pack option and filed next to {@code packMaxSize}. A reservation is the way out. It is checked
 * before the whole chain, so a claimed prefix decides the tab whatever words the rest of the name spells.
 *
 * <p>Nothing here is a naming authority: a prefix is claimed on a first-come basis and the refusals below
 * exist to protect options that already exist, not to arbitrate between two addons.
 */
public final class ConfigCategories {
    private ConfigCategories() {}

    // Keyed by the lower-cased prefix, matching ConfigCategory's own case-insensitivity. Concurrent because
    // addon entry points are called in load order on the mod-init thread while nothing else writes, but the
    // GUI reads this map on the render thread for the life of the game.
    private static final Map<String, String> RESERVED = new ConcurrentHashMap<>();

    // Under three characters a prefix stops being a namespace and becomes a trawl: "s" would capture
    // soundBaseRadius, specialBomberFuse and sunImmunity in one claim.
    private static final int MIN_PREFIX = 3;

    /**
     * Claim {@code prefix} for {@code category}, so every option whose name starts with it lands on that tab.
     *
     * <p>Claiming the same prefix for the same tab twice is allowed and does nothing, because a mod loaded
     * beside a fork of itself must not fail on the second call.
     *
     * @throws IllegalArgumentException if the prefix is too short, already claimed by someone else, or would
     *         capture an option that is already in the schema. The last one is asked of the live schema
     *         rather than of a keyword list: what counts is the options that actually exist at the moment of
     *         the claim, which is exactly the shipped set plus whichever addons were loaded first.
     */
    public static void reserve(String prefix, String category) {
        if (prefix == null || prefix.trim().length() < MIN_PREFIX) {
            throw new IllegalArgumentException("a config prefix needs at least " + MIN_PREFIX
                    + " characters, got: " + prefix);
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("no category given for config prefix " + prefix);
        }
        String key = prefix.trim().toLowerCase(Locale.ROOT);
        String held = RESERVED.get(key);
        if (held != null) {
            if (held.equals(category)) {
                return;
            }
            throw new IllegalArgumentException("config prefix " + prefix + " is already held by " + held);
        }
        for (Field f : ConfigSchema.all()) {
            if (f.getName().toLowerCase(Locale.ROOT).startsWith(key)) {
                throw new IllegalArgumentException("config prefix " + prefix + " would capture the existing "
                        + "option " + f.getName());
            }
        }
        RESERVED.put(key, category);
    }

    /** The tab claimed for this option, or null when nobody claimed a prefix it starts with. */
    static String categoryFor(String optionName) {
        String n = optionName.toLowerCase(Locale.ROOT);
        String best = null;
        int bestLength = 0;
        // Longest claim wins, so a mod claiming "mymodwater" is not swallowed by another's "mymod".
        for (Map.Entry<String, String> e : RESERVED.entrySet()) {
            if (n.startsWith(e.getKey()) && e.getKey().length() > bestLength) {
                best = e.getValue();
                bestLength = e.getKey().length();
            }
        }
        return best;
    }

    /**
     * Drop a claim. Exists for tests, which share one JVM and one map: a claim left behind would move
     * another test's option onto a tab it never asked for. Nothing in production releases a prefix.
     */
    static void release(String prefix) {
        RESERVED.remove(prefix.trim().toLowerCase(Locale.ROOT));
    }
}
