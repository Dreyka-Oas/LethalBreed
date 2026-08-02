package com.dreykaoas.lethalbreed.config;

import com.dreykaoas.lethalbreed.LethalBreed;

import java.nio.file.Path;

/**
 * Telling the operator what is wrong with the shape of their config file.
 *
 * <p>The load loop is field-driven, not file-driven — it never looks at a key the schema does not have — so
 * without this a misspelled option is invisible: the edit does nothing, the summary line still reports
 * success, and the next write deletes the line. The user watches their edit vanish with no explanation.
 */
public final class ConfigDriftReport {
    private ConfigDriftReport() {}

    /** Say what is wrong with the file's shape, once per problem, naming the offending key. */
    public static void emit(ConfigStructure.Report report, Path path) {
        for (ConfigStructure.Unknown u : report.unknown()) {
            if (u.suggestion() != null) {
                LethalBreed.LOGGER.warn(
                        "[LethalBreed] unknown option '{}' in {} — did you mean '{}'? It does nothing and "
                                + "will be dropped when the file is rewritten.",
                        u.name(), path.getFileName(), u.suggestion());
            } else {
                LethalBreed.LOGGER.warn(
                        "[LethalBreed] unknown option '{}' in {} — it does nothing and will be dropped "
                                + "when the file is rewritten.",
                        u.name(), path.getFileName());
            }
        }
        for (String d : report.duplicated()) {
            LethalBreed.LOGGER.warn(
                    "[LethalBreed] option '{}' appears under more than one category — only one copy is "
                            + "read, and which one wins is not something you should rely on.", d);
        }
        for (String c : report.bogusCategory()) {
            LethalBreed.LOGGER.warn(
                    "[LethalBreed] '{}' is not a config category — its options are still read by name, "
                            + "but they will be moved to their real category on the next write.", c);
        }
        if (!report.misplaced().isEmpty()) {
            // Expected during the flat -> nested migration and corrected automatically, so this is
            // information, not a problem the user has to act on.
            LethalBreed.LOGGER.info("[LethalBreed] {} option(s) filed under the wrong category — "
                    + "moving them on the next write.", report.misplaced().size());
        }
    }
}
