package com.dreykaoas.lethalbreed.config.io;

import com.dreykaoas.lethalbreed.config.ConfigAccess;
import com.dreykaoas.lethalbreed.config.schema.ConfigSchema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless characterization of the load path, reachable only because {@link ConfigLoader#load} takes the
 * path: {@code ConfigIo.load()} resolves it through {@code FabricLoader}, which the test source set has no
 * runtime for. Every case runs against a {@code @TempDir}, so a real config can never be touched.
 */
class ConfigLoaderTest {

    @TempDir Path dir;

    private Path write(String json) throws Exception {
        Path cfg = dir.resolve("lethalbreed.json");
        Files.writeString(cfg, json);
        return cfg;
    }

    /** The bug: the report describes the file that was just moved aside and replaced. Serving it afterwards
     *  makes the operator join notice nag, for the whole session, about problems in a file that no longer
     *  exists. */
    @Test
    void reportsNothingOnceAnUnusableFileHasBeenQuarantined() throws Exception {
        Path cfg = write("{\"Nonsense\": 1, \"AlsoNonsense\": 2}");
        assertNull(ConfigLoader.load(cfg),
                "the config now running is pristine defaults — there is nothing left to warn about");
    }

    @Test
    void quarantineKeepsTheOriginalAndWritesFreshDefaults() throws Exception {
        Path cfg = write("{\"Nonsense\": 1, \"AlsoNonsense\": 2}");
        ConfigLoader.load(cfg);

        assertTrue(Files.exists(cfg), "a fresh default file must be written in its place");
        assertTrue(Files.readString(cfg).contains("tickBuckets"), "the fresh file must hold the schema");

        List<Path> archives;
        try (Stream<Path> s = Files.list(dir)) {
            archives = s.filter(p -> p.getFileName().toString().contains(".old-")).toList();
        }
        assertFalse(archives.isEmpty(), "the unreadable content must survive as an archive");
        assertTrue(Files.readString(archives.get(0)).contains("Nonsense"),
                "the archive must be the ORIGINAL bytes, not a rewrite");
    }

    /** The non-regression: a file that still has recognisable options is NOT quarantined, so its drift
     *  report is exactly what the operator needs and must still be served. */
    @Test
    void aFileThatStillHasRealOptionsKeepsReportingItsDrift() throws Exception {
        Field known = ConfigSchema.find("tickBuckets");
        assertNotNull(known);
        String current = ConfigAccess.read(known);   // write the value back unchanged: applying is a no-op

        Path cfg = write("{\"Perf\": {\"tickBuckets\": " + current + "}, \"daySleepEnabld\": true}");
        ConfigStructure.Report report = ConfigLoader.load(cfg);

        assertNotNull(report, "nothing was quarantined, so the drift still describes the live file");
        assertEquals(List.of(new ConfigStructure.Rename("daySleepEnabld", "daySleepEnabled")),
                report.renamed(), "the misspelled key must be repaired, not merely reported");
    }

    /** End to end: a misspelled key must both take effect in memory and come out corrected on disk.
     *  Repairing the report without carrying the value would be the same silent no-op as before. */
    @Test
    void aMisspelledKeyIsAppliedAndRewrittenUnderItsRealName() throws Exception {
        Field target = ConfigSchema.find("daySleepEnabled");
        assertNotNull(target);
        String original = ConfigAccess.read(target);
        boolean flipped = !Boolean.parseBoolean(original);
        try {
            Path cfg = write("{\"Mood\": {\"daySleepEnabld\": " + flipped + "}}");
            ConfigLoader.load(cfg);

            assertEquals(String.valueOf(flipped), ConfigAccess.read(target),
                    "the value on the misspelled line must reach the real option");
            String rewritten = Files.readString(cfg);
            assertTrue(rewritten.contains("daySleepEnabled"), "the corrected name must be on disk");
            assertFalse(rewritten.contains("daySleepEnabld\""), "the typo must be gone from the file");
        } finally {
            // The config is static: leaving it flipped would leak into whatever test runs next.
            ConfigAccess.apply("daySleepEnabled", original, false);
        }
    }

    @Test
    void anAbsentFileReportsNothingAndWritesDefaults() throws Exception {
        Path cfg = dir.resolve("lethalbreed.json");
        assertNull(ConfigLoader.load(cfg), "there was no file to have drift");
        assertTrue(Files.exists(cfg));
    }

    @Test
    void anUnparseableFileIsQuarantinedAndReportsNothing() throws Exception {
        Path cfg = write("{ this is not json");
        assertNull(ConfigLoader.load(cfg));
        assertTrue(Files.exists(cfg), "a fresh default file must be written in its place");
        try (Stream<Path> s = Files.list(dir)) {
            assertTrue(s.anyMatch(p -> p.getFileName().toString().contains(".old-")));
        }
    }
}
