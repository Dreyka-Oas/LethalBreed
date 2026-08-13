package com.dreykaoas.lethalbreed.config.io;

import java.util.Set;

/**
 * "Did you mean …?" for a config key the schema does not know.
 *
 * <p>Split out of {@code ConfigStructure}, which mixed the SHAPE analysis of the file with the string-distance
 * search used to explain one of its findings. The two answer different questions and change for different
 * reasons: what counts as drift is a decision about the config format, while how close two names are is a
 * decision about typing mistakes.
 *
 * <p>Package-private on purpose — the suggestion is part of a drift report, never an API of its own.
 */
final class NameSuggest {
    private NameSuggest() {}

    /** Closest known name within an edit-distance budget that scales with length, so "tickBucket" finds
     *  "tickBuckets" but a name sharing nothing with any option returns null rather than a nonsense
     *  suggestion. Ties resolve to the alphabetically first candidate so the answer is deterministic. */
    static String suggest(String name, Set<String> knownNames) {
        return best(name, knownNames, false);
    }

    /** Same search, but {@code null} as soon as two candidates sit at the same best distance.
     *
     *  <p>{@link #suggest} is allowed to break a tie alphabetically because it only ever prints
     *  "did you mean …?" and the user decides. This one feeds the automatic repair in
     *  {@code ConfigStructure}, which rewrites the file without asking: a coin flip between two
     *  equally plausible options would silently apply the user's value to the wrong setting, which
     *  is strictly worse than dropping it and saying so. */
    static String suggestUnique(String name, Set<String> knownNames) {
        return best(name, knownNames, true);
    }

    private static String best(String name, Set<String> knownNames, boolean requireUnique) {
        int budget = Math.max(2, name.length() / 4);
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        int atBestDistance = 0;
        for (String candidate : knownNames) {
            int d = distance(name, candidate, budget);
            if (d > budget) {
                continue;
            }
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
                atBestDistance = 1;
            } else if (d == bestDistance) {
                atBestDistance++;
                if (candidate.compareTo(best) < 0) {
                    best = candidate;
                }
            }
        }
        return requireUnique && atBestDistance > 1 ? null : best;
    }

    /** Levenshtein distance, two-row form, abandoning early once every cell in a row exceeds the budget
     *  (the distance can only grow from there, so a further row cannot bring it back under). */
    static int distance(String a, String b, int budget) {
        if (Math.abs(a.length() - b.length()) > budget) {
            return budget + 1;
        }
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            int rowMin = curr[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, curr[j]);
            }
            if (rowMin > budget) {
                return budget + 1;
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[b.length()];
    }
}
