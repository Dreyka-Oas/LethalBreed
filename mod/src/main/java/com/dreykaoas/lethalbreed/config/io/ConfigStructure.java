package com.dreykaoas.lethalbreed.config.io;

import com.dreykaoas.lethalbreed.config.ConfigBounds;
import com.dreykaoas.lethalbreed.config.schema.ConfigCategory;
import com.dreykaoas.lethalbreed.config.schema.ConfigFields;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Checks the SHAPE of the config file — never its values.
 *
 * <p>A user is free to set any number they like; an out-of-range one is clamped by
 * {@link ConfigBounds} on the way in, deliberately and silently. What this class looks for is a file
 * whose <em>keys</em> no longer correspond to the options the mod actually has: a misspelled name, an
 * option filed under two categories at once, a category that does not exist, or a file where nothing
 * at all is recognisable.
 *
 * <p>It lives here rather than in {@link ConfigIo} so it can be unit-tested: ConfigIo imports
 * FabricLoader and the mod entrypoint's logger and therefore cannot load under plain JUnit. Same
 * reason {@code ContaminationRoll} and {@code PlacedBlockPolicy} were extracted. Keep it free of
 * Minecraft types, file I/O and logging — the caller reports.
 *
 * <p>It reads BOTH layouts, exactly as {@code ConfigIo.load()} does: the current nested one (a
 * JsonObject per category) and the old flat one (every option on the root). A flat file is old, not
 * broken, and must never be reported as drift — every pre-migration user has one on disk.
 */
public final class ConfigStructure {
    private ConfigStructure() {}

    /** An option name in the file that matches no real option, with the closest real name when one is
     *  near enough to be worth suggesting ({@code null} when nothing is close — a wild guess is worse
     *  than no guess). */
    public record Unknown(String name, String suggestion) {}

    /**
     * What the file looks like compared with the schema.
     *
     * @param keysInFile   every option-position key seen, at the root or inside a category
     * @param recognised   how many of those name a real option
     * @param unknown      names matching no option — the user's edit does nothing and will be dropped
     * @param duplicated   options appearing under two or more categories; the flatten is last-wins
     * @param bogusCategory root objects whose name is not a real category
     * @param misplaced    right name, wrong parent category — tolerated and auto-corrected by save()
     */
    public record Report(
            int keysInFile,
            int recognised,
            List<Unknown> unknown,
            List<String> duplicated,
            List<String> bogusCategory,
            List<String> misplaced) {

        /** True when the file had content but not one key of it was recognisable. That is the strongest
         *  corruption signal available, and the only one that justifies regenerating: anything less and
         *  rewriting would throw away the settings that ARE still readable. */
        public boolean unusable() {
            return keysInFile > 0 && recognised == 0;
        }

        /** True when there is nothing at all to tell the user about. */
        public boolean clean() {
            return unknown.isEmpty() && duplicated.isEmpty()
                    && bogusCategory.isEmpty() && misplaced.isEmpty();
        }

        /** Total number of distinct problems, for a one-line summary. */
        public int problemCount() {
            return unknown.size() + duplicated.size() + bogusCategory.size() + misplaced.size();
        }
    }

    /**
     * Compare a parsed config file against the set of option names the mod actually has.
     *
     * @param root       the parsed file; must be a JSON object (a non-object root never reaches here,
     *                   {@code getAsJsonObject()} throws first and ConfigIo quarantines)
     * @param knownNames every real option name — pass {@code ConfigFields.all()} names in production;
     *                   taking it as a parameter keeps this testable against a small fixed set
     */
    public static Report check(JsonObject root, Set<String> knownNames) {
        Set<String> knownCategories = new HashSet<>();
        for (String name : knownNames) {
            knownCategories.add(ConfigCategory.of(name));
        }

        int keysInFile = 0;
        int recognised = 0;
        List<Unknown> unknown = new ArrayList<>();
        List<String> duplicated = new ArrayList<>();
        List<String> bogusCategory = new ArrayList<>();
        List<String> misplaced = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            if (!value.isJsonObject()) {
                // Flat layout: the root member IS an option. No category to check against.
                keysInFile++;
                if (knownNames.contains(key)) {
                    recognised++;
                    if (!seen.add(key)) {
                        duplicated.add(key);
                    }
                } else {
                    unknown.add(new Unknown(key, NameSuggest.suggest(key, knownNames)));
                }
                continue;
            }

            // Nested layout: the root member is a category holding options.
            if (!knownCategories.contains(key)) {
                bogusCategory.add(key);
            }
            for (Map.Entry<String, JsonElement> inner : value.getAsJsonObject().entrySet()) {
                String name = inner.getKey();
                keysInFile++;
                if (!knownNames.contains(name)) {
                    unknown.add(new Unknown(name, NameSuggest.suggest(name, knownNames)));
                    continue;
                }
                recognised++;
                if (!seen.add(name)) {
                    duplicated.add(name);
                } else if (!ConfigCategory.of(name).equals(key)) {
                    // Only worth reporting once, and only for a category that exists — an option inside
                    // a bogus category is already covered by the bogusCategory entry above.
                    if (knownCategories.contains(key)) {
                        misplaced.add(name);
                    }
                }
            }
        }

        return new Report(keysInFile, recognised,
                List.copyOf(unknown), List.copyOf(duplicated),
                List.copyOf(bogusCategory), List.copyOf(misplaced));
    }

}
