package com.dreykaoas.lethalbreed.config;

import com.dreykaoas.lethalbreed.LethalBreed;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Archiving an unusable config file so the user never loses its content.
 *
 * <p>Split out of {@code ConfigIo}, which held loading, writing, archiving and operator reporting in one
 * 309-line class.
 */
public final class ConfigQuarantine {
    private ConfigQuarantine() {}

    /** Move an unusable config aside so the user keeps its content, then trim the archive to
     *  {@link ConfigBackup#KEEP}. Returns true when the original is safely out of the way and it is
     *  therefore sound to write a fresh default file in its place.
     *
     *  <p>Called for a file that cannot be parsed at all, and for one that parses but contains no
     *  recognisable option. The suffix is {@code .old-} as of the structure-check release; archives
     *  written by older versions carry {@code .corrupt-} and are deliberately never pruned. */
    public static boolean moveAside(Path path, String cause) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        try {
            Path aside = ConfigBackup.archive(path, stamp);
            LethalBreed.LOGGER.error(
                    "[LethalBreed] config unusable ({}) — moved to {} and rewritten with defaults. "
                            + "Fix that file and rename it back to keep your settings.",
                    cause, aside);
            try {
                for (Path pruned : ConfigBackup.prune(path.getParent(), path.getFileName().toString(),
                        ConfigBackup.KEEP)) {
                    LethalBreed.LOGGER.info("[LethalBreed] removed old config backup {}", pruned);
                }
            } catch (IOException pruneFailed) {
                // Pruning is housekeeping. Failing it must not undo the archive we just made, which is
                // the part that actually protects the user's settings.
                LethalBreed.LOGGER.warn("[LethalBreed] could not trim old config backups: {}",
                        pruneFailed.toString());
            }
            return true;
        } catch (IOException moveFailed) {
            LethalBreed.LOGGER.error(
                    "[LethalBreed] config unusable ({}) AND could not be moved aside ({}) — running on "
                            + "defaults, leaving {} untouched.",
                    cause, moveFailed.toString(), path);
            return false;
        }
    }
}
