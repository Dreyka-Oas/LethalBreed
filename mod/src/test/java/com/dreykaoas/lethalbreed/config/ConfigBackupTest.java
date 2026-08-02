package com.dreykaoas.lethalbreed.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless characterization of {@link ConfigBackup} — the {@code .old-} rotation.
 *
 * <p>These run under plain JUnit because ConfigBackup takes explicit {@link Path} arguments and uses
 * nothing but {@code java.nio.file}: no logger, no FabricLoader. {@code @TempDir} gives each test a
 * throwaway directory, so nothing here can touch a real config.
 *
 * <p>What the tests are really guarding is that a backup is a MOVE and never a delete. A player's
 * config can represent hours of tuning; the whole point of archiving instead of removing is that a
 * mistake stays recoverable.
 */
class ConfigBackupTest {

    @Test
    void archiveMovesTheFileAndLeavesNothingBehind(@TempDir Path dir) throws Exception {
        Path f = Files.writeString(dir.resolve("lethalbreed.json"), "{\"a\":1}");

        Path moved = ConfigBackup.archive(f, "20260802-120000-000");

        assertFalse(Files.exists(f), "the original must be gone, not copied");
        assertEquals("lethalbreed.json.old-20260802-120000-000", moved.getFileName().toString());
        assertEquals("{\"a\":1}", Files.readString(moved), "content must survive intact");
    }

    @Test
    void pruneKeepsTheThreeNewestAndDeletesOlderOnes(@TempDir Path dir) throws Exception {
        for (String s : List.of("20260801-100000-000", "20260802-100000-000",
                "20260803-100000-000", "20260804-100000-000", "20260805-100000-000")) {
            Files.writeString(dir.resolve("lethalbreed.json.old-" + s), s);
        }

        List<Path> deleted = ConfigBackup.prune(dir, "lethalbreed.json", 3);

        assertEquals(2, deleted.size());
        assertTrue(Files.exists(dir.resolve("lethalbreed.json.old-20260805-100000-000")));
        assertTrue(Files.exists(dir.resolve("lethalbreed.json.old-20260804-100000-000")));
        assertTrue(Files.exists(dir.resolve("lethalbreed.json.old-20260803-100000-000")));
        assertFalse(Files.exists(dir.resolve("lethalbreed.json.old-20260802-100000-000")));
        assertFalse(Files.exists(dir.resolve("lethalbreed.json.old-20260801-100000-000")));
    }

    @Test
    void pruneIsANoOpBelowTheLimit(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("lethalbreed.json.old-20260801-100000-000"), "x");

        assertTrue(ConfigBackup.prune(dir, "lethalbreed.json", 3).isEmpty());
        assertTrue(Files.exists(dir.resolve("lethalbreed.json.old-20260801-100000-000")));
    }

    @Test
    void pruneIgnoresUnrelatedFiles(@TempDir Path dir) throws Exception {
        // The config directory is shared. Deleting anything we did not write would be a bug with a
        // blast radius outside this mod entirely.
        Files.writeString(dir.resolve("lethalbreed.json"), "live");
        Files.writeString(dir.resolve("othermod.json"), "other");
        Files.writeString(dir.resolve("lethalbreed.json.corrupt-20260801-100000-000"), "legacy");
        for (int i = 1; i <= 4; i++) {
            Files.writeString(dir.resolve("lethalbreed.json.old-2026080" + i + "-100000-000"), "b");
        }

        ConfigBackup.prune(dir, "lethalbreed.json", 3);

        assertEquals("live", Files.readString(dir.resolve("lethalbreed.json")),
                "the live config must never be pruned");
        assertTrue(Files.exists(dir.resolve("othermod.json")));
        assertTrue(Files.exists(dir.resolve("lethalbreed.json.corrupt-20260801-100000-000")),
                "pre-existing .corrupt- backups from older versions must be left alone");
    }

    @Test
    void pruneOnAnEmptyDirectoryDoesNotThrow(@TempDir Path dir) throws Exception {
        assertTrue(ConfigBackup.prune(dir, "lethalbreed.json", 3).isEmpty());
    }
}
