package com.dreykaoas.lethalbreed.config.io.diag;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deciding what an unrecognised option name was meant to be.
 *
 * <p>Two sources, in priority order: the explicit rename table, then edit distance. A candidate is only
 * ever a candidate here; whether the repair actually happens is settled by {@link ConfigStructure} once
 * the whole file has been read, because two typos converging on one option are as ambiguous as one typo
 * between two options.
 */
final class RenameRepair {
    private RenameRepair() {}

    /** Record one unrecognised key: the name to show the user, and the option its value can be moved to
     *  if the repair turns out to be unambiguous once the rest of the file has been read. */
    static void collect(String name, Set<String> knownNames, Set<String> present,
                                List<ConfigDrift.Unknown> candidates, List<String> targets,
                                Map<String, Integer> claims) {
        // An explicit rename beats a guess, so it is checked first: a deliberate vocabulary change carries
        // the user's value across even when the two names share almost no letters, exactly the case edit
        // distance cannot serve, and exactly where a fuzzy hit would be a coin flip.
        String renamedTo = ConfigLegacyNames.newNameOf(name);
        if (renamedTo != null && knownNames.contains(renamedTo)) {
            candidates.add(new ConfigDrift.Unknown(name, renamedTo));
            String target = present.contains(renamedTo) ? null : renamedTo;
            targets.add(target);
            if (target != null) {
                claims.merge(target, 1, Integer::sum);
            }
            return;
        }

        candidates.add(new ConfigDrift.Unknown(name, NameSuggest.suggest(name, knownNames)));
        String target = NameSuggest.suggestUnique(name, knownNames);
        if (target != null && present.contains(target)) {
            target = null;
        }
        targets.add(target);
        if (target != null) {
            claims.merge(target, 1, Integer::sum);
        }
    }}
