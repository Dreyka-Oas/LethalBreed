package com.dreykaoas.lethalbreed.config.io;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Archives a config file aside instead of destroying it, and keeps the archive from growing forever.
 *
 * <p>Every path that replaces the user's config goes through here. A config can represent hours of
 * tuning, so the mod never deletes one outright: it renames it to
 * {@code lethalbreed.json.old-yyyyMMdd-HHmmss-SSS} and writes a fresh file alongside. The timestamp
 * format is the one {@code ConfigIo.quarantine()} already used, chosen because it sorts
 * lexicographically in chronological order — which is what makes {@link #prune} a plain sort with no
 * bookkeeping file to keep in sync.
 *
 * <p>Deliberately free of Minecraft types, logging and FabricLoader: it takes explicit paths and
 * returns what it did, so the caller reports and so this is testable under plain JUnit with
 * {@code @TempDir}.
 */
public final class ConfigBackup {
    private ConfigBackup() {}

    /** How many archives to keep. Beyond this the oldest are removed — the recent ones are the ones
     *  worth recovering, and an unbounded pile of dead configs is its own kind of mess. */
    public static final int KEEP = 3;

    private static final String SUFFIX = ".old-";

    /**
     * Move {@code file} aside, returning where it went.
     *
     * <p>A move, never a copy-then-delete: the original content must survive even if the process dies
     * mid-operation, and a rename is the only way to get that for free.
     *
     * @param stamp timestamp component, expected in {@code yyyyMMdd-HHmmss-SSS} form so archives sort
     *              chronologically by name
     */
    public static Path archive(Path file, String stamp) throws IOException {
        Path aside = file.resolveSibling(file.getFileName() + SUFFIX + stamp);
        Files.move(file, aside);
        return aside;
    }

    /**
     * Delete all but the {@code keep} newest archives of {@code baseName} in {@code dir}, returning
     * what was removed.
     *
     * <p>Matches only {@code <baseName>.old-*}. The live config, other mods' files, and the
     * {@code .corrupt-} archives written by older versions of this mod are all left untouched — the
     * config directory is shared, and deleting something we did not write would have a blast radius
     * outside this mod entirely.
     */
    public static List<Path> prune(Path dir, String baseName, int keep) throws IOException {
        String prefix = baseName + SUFFIX;
        List<Path> archives = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, prefix + "*")) {
            for (Path p : stream) {
                archives.add(p);
            }
        }
        if (archives.size() <= keep) {
            return List.of();
        }
        // Newest first: the timestamp suffix makes filename order the same as chronological order, so
        // this needs no file-attribute lookup and cannot be fooled by a touched mtime.
        archives.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());

        List<Path> deleted = new ArrayList<>();
        for (Path stale : archives.subList(keep, archives.size())) {
            if (Files.deleteIfExists(stale)) {
                deleted.add(stale);
            }
        }
        return List.copyOf(deleted);
    }
}
