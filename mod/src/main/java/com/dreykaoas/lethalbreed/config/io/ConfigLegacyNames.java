package com.dreykaoas.lethalbreed.config.io;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Option names this mod used to ship, mapped to the name it ships now.
 *
 * <p>Renaming a config option is an on-disk break: {@link ConfigLoader}'s apply loop is field-driven and
 * never looks at a key the schema does not have, so an un-aliased rename drops the user's value and the
 * write that follows deletes the line. {@link NameSuggest} already repairs <em>typos</em> by edit distance,
 * but a deliberate rename is not a typo — {@code specialHurleurRadius -> specialScreamerRadius} is six
 * edits on a budget of four, and a fuzzy match that did happen to fire would be a guess. This table is the
 * exact answer, and {@link ConfigStructure} consults it before falling back to the distance search.
 *
 * <p>Entries are permanent. Dropping one silently resets that option for anyone who has not launched the
 * game since the rename — which is the whole failure this class exists to prevent.
 */
public final class ConfigLegacyNames {
    private ConfigLegacyNames() {}

    private static final Map<String, String> RENAMES = new LinkedHashMap<>();

    static {
        // Filled by Task 4 — the French -> English special-variant vocabulary.
    }

    /** The current name for an option that used to be called {@code oldName}, or null if it is not a name
     *  this mod ever shipped. */
    public static String newNameOf(String oldName) {
        return RENAMES.get(oldName);
    }

    /** The whole table, for tests and for the drift report. */
    public static Map<String, String> all() {
        return Map.copyOf(RENAMES);
    }
}
