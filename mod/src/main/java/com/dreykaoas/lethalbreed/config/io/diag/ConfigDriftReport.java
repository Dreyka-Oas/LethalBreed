package com.dreykaoas.lethalbreed.config.io.diag;

import com.dreykaoas.lethalbreed.LethalBreed;

import java.nio.file.Path;

/**
 * Telling the operator what is wrong with the shape of their config file.
 *
 * <p>The load loop is field-driven, not file-driven (it never looks at a key the schema does not have), so
 * without this a misspelled option is invisible: the edit does nothing, the summary line still reports
 * success, and the next write deletes the line. The user watches their edit vanish with no explanation.
 */
public final class ConfigDriftReport {
    private ConfigDriftReport() {}

    /** Say what is wrong with the file's shape, once per problem, naming the offending key. Drift the
     *  loader repairs by itself is stated at INFO, as a record of what happened, with nothing to warn
     *  about. */
    public static void emit(ConfigDrift.Report report, Path path) {
        for (ConfigDrift.Rename r : report.renamed()) {
            LethalBreed.LOGGER.info(
                    "[LethalBreed] misspelled option '{}' in {}, reading it as '{}'; your value is kept "
                            + "and the file is corrected on the next write.",
                    r.from(), path.getFileName(), r.to());
        }
        for (ConfigDrift.Unknown u : report.unknown()) {
            if (u.suggestion() != null) {
                // Reaching here with a suggestion means the repair was NOT safe to make: either that
                // option is already set elsewhere in the file, or another name is just as close.
                LethalBreed.LOGGER.warn(
                        "[LethalBreed] unknown option '{}' in {}. Did you mean '{}'? Too ambiguous to "
                                + "correct automatically; it does nothing and will be dropped when the "
                                + "file is rewritten.",
                        u.name(), path.getFileName(), u.suggestion());
            } else {
                LethalBreed.LOGGER.warn(
                        "[LethalBreed] unknown option '{}' in {}: it does nothing and will be dropped "
                                + "when the file is rewritten.",
                        u.name(), path.getFileName());
            }
        }
        for (String d : report.duplicated()) {
            LethalBreed.LOGGER.warn(
                    "[LethalBreed] option '{}' appears under more than one category, only one copy is "
                            + "read, and which one wins is not something you should rely on.", d);
        }
        for (String c : report.bogusCategory()) {
            // Corrected by the write that follows this read, exactly like a misplaced option, so the
            // operator has nothing to act on here.
            LethalBreed.LOGGER.info(
                    "[LethalBreed] '{}' is not a config category: its options are still read by name, "
                            + "and they move to their real category on the next write.", c);
        }
        if (!report.misplaced().isEmpty()) {
            // Expected during the flat -> nested migration and corrected automatically, so this is
            // information, not a problem the user has to act on.
            LethalBreed.LOGGER.info("[LethalBreed] {} option(s) filed under the wrong category, "
                    + "moving them on the next write.", report.misplaced().size());
        }
    }
}
