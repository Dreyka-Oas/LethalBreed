package com.dreykaoas.lethalbreed.config.io;

import com.dreykaoas.lethalbreed.config.ConfigBounds;
import com.dreykaoas.lethalbreed.config.schema.ConfigCategory;
import com.dreykaoas.lethalbreed.config.schema.ConfigFields;

import java.util.ArrayList;
import java.util.HashMap;
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

    /** An option name in the file that matches no real option and could not be repaired, with the
     *  closest real name when one is near enough to be worth suggesting ({@code null} when nothing is
     *  close — a wild guess is worse than no guess). */
    public record Unknown(String name, String suggestion) {}

    /** A misspelling the loader resolves by itself: {@code from} is the key in the file, {@code to} the
     *  real option that its value is applied to. The next write emits {@code to} and the typo is gone. */
    public record Rename(String from, String to) {}

    /**
     * What the file looks like compared with the schema.
     *
     * @param keysInFile   every option-position key seen, at the root or inside a category
     * @param recognised   how many of those name a real option
     * @param unknown      names matching no option and not repairable — the user's edit is lost
     * @param renamed      misspellings resolved automatically; the value is kept, the key rewritten
     * @param duplicated   options appearing under two or more categories; the flatten is last-wins
     * @param bogusCategory root objects whose name is not a real category
     * @param misplaced    right name, wrong parent category — tolerated and auto-corrected by save()
     */
    public record Report(
            int keysInFile,
            int recognised,
            List<Unknown> unknown,
            List<Rename> renamed,
            List<String> duplicated,
            List<String> bogusCategory,
            List<String> misplaced) {

        /** True when the file had content but not one key of it was recognisable. That is the strongest
         *  corruption signal available, and the only one that justifies regenerating: anything less and
         *  rewriting would throw away the settings that ARE still readable. A file whose keys are all
         *  typos is not unusable — every one of them is about to be repaired and applied. */
        public boolean unusable() {
            return keysInFile > 0 && recognised == 0 && renamed.isEmpty();
        }

        /** True when nothing in the file needs the user.
         *
         *  <p>Drift the loader repairs on its own does not count. A renamed typo, an option under the
         *  wrong category and a stale category name are all corrected by the write that follows this
         *  read, so the user has no decision to make and nothing to be warned about — telling them
         *  would be nagging about a file that is already fixed. What remains actionable is a key whose
         *  value is genuinely lost ({@code unknown}) or ambiguous ({@code duplicated}). */
        public boolean clean() {
            return unknown.isEmpty() && duplicated.isEmpty();
        }

        /** Number of problems the user has to act on, for a one-line summary. */
        public int problemCount() {
            return unknown.size() + duplicated.size();
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

        // Every real option the file already sets, gathered before anything is classified. A typo may
        // only be repaired onto an option the file does not already carry: otherwise "tickBucket" next
        // to a deliberate "tickBuckets" would overwrite the value the user actually wrote.
        Set<String> present = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                for (String inner : entry.getValue().getAsJsonObject().keySet()) {
                    if (knownNames.contains(inner)) {
                        present.add(inner);
                    }
                }
            } else if (knownNames.contains(entry.getKey())) {
                present.add(entry.getKey());
            }
        }

        int keysInFile = 0;
        int recognised = 0;
        List<Unknown> unknown = new ArrayList<>();
        List<String> duplicated = new ArrayList<>();
        List<String> bogusCategory = new ArrayList<>();
        List<String> misplaced = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // Unrecognised keys in file order, each with the name to print and the name to repair onto
        // (null when there is nothing safe to repair onto). Split into unknown/renamed once the whole
        // file has been read, because whether a repair is safe depends on the other keys.
        List<Unknown> candidates = new ArrayList<>();
        List<String> targets = new ArrayList<>();
        Map<String, Integer> claims = new HashMap<>();

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
                    collect(key, knownNames, present, candidates, targets, claims);
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
                    collect(name, knownNames, present, candidates, targets, claims);
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

        List<Rename> renamed = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            String target = targets.get(i);
            // Two typos converging on one option are as ambiguous as one typo between two options:
            // neither can be repaired without guessing which line the user meant.
            if (target != null && claims.get(target) == 1) {
                renamed.add(new Rename(candidates.get(i).name(), target));
            } else {
                unknown.add(candidates.get(i));
            }
        }

        return new Report(keysInFile, recognised,
                List.copyOf(unknown), List.copyOf(renamed), List.copyOf(duplicated),
                List.copyOf(bogusCategory), List.copyOf(misplaced));
    }

    /** Record one unrecognised key: the name to show the user, and the option its value can be moved to
     *  if the repair turns out to be unambiguous once the rest of the file has been read. */
    private static void collect(String name, Set<String> knownNames, Set<String> present,
                                List<Unknown> candidates, List<String> targets,
                                Map<String, Integer> claims) {
        candidates.add(new Unknown(name, NameSuggest.suggest(name, knownNames)));
        String target = NameSuggest.suggestUnique(name, knownNames);
        if (target != null && present.contains(target)) {
            target = null;
        }
        targets.add(target);
        if (target != null) {
            claims.merge(target, 1, Integer::sum);
        }
    }

}
