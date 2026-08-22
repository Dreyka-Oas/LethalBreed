package com.dreykaoas.lethalbreed.config.io.diag;

import java.util.List;

/**
 * What a parsed config file looks like compared with the schema, and the two kinds of drift worth naming
 * on their own. Produced by {@link ConfigStructure#check}, consumed by {@code ConfigIo} to decide whether
 * to quarantine, and by {@code LifecycleInit} to decide what to tell the operator.
 *
 * <p>Held apart from the walk that fills it because these are the vocabulary of the whole config-diagnosis
 * path, read by three packages, while the walk is an implementation detail of one method.
 */
public final class ConfigDrift {
    private ConfigDrift() {}

    /** An option name in the file that matches no real option and could not be repaired, with the
     *  closest real name when one is near enough to be worth suggesting ({@code null} when nothing is
     *  close, since a wild guess is worse than no guess). */
    public record Unknown(String name, String suggestion) {}

    /** A misspelling the loader resolves by itself: {@code from} is the key in the file, {@code to} the
     *  real option that its value is applied to. The next write emits {@code to} and the typo is gone. */
    public record Rename(String from, String to) {}

    /**
     * What the file looks like compared with the schema.
     *
     * @param keysInFile   every option-position key seen, at the root or inside a category
     * @param recognised   how many of those name a real option
     * @param unknown      names matching no option and not repairable: the user's edit is lost
     * @param renamed      misspellings resolved automatically; the value is kept, the key rewritten
     * @param duplicated   options appearing under two or more categories; the flatten is last-wins
     * @param bogusCategory root objects whose name is not a real category
     * @param misplaced    right name, wrong parent category (tolerated and auto-corrected by save())
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
         *  typos is not unusable: every one of them is about to be repaired and applied. */
        public boolean unusable() {
            return keysInFile > 0 && recognised == 0 && renamed.isEmpty();
        }

        /** True when nothing in the file needs the user.
         *
         *  <p>Drift the loader repairs on its own does not count. A renamed typo, an option under the
         *  wrong category and a stale category name are all corrected by the write that follows this
         *  read, so the user has no decision to make and nothing to be warned about. Telling them
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

}
