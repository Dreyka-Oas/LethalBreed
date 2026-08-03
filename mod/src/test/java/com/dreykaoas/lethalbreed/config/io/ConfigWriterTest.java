package com.dreykaoas.lethalbreed.config.io;

import com.dreykaoas.lethalbreed.config.ConfigSchema;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless characterization of the config writer. This is only reachable because {@link ConfigWriter#save}
 * takes the destination path — {@code ConfigIo.save()} resolves it through {@code FabricLoader}, which does
 * not exist in the test source set.
 */
class ConfigWriterTest {

    @TempDir Path dir;

    @Test
    void writesEveryOptionGroupedByCategory() throws Exception {
        Path out = dir.resolve("lethalbreed.json");
        ConfigWriter.save(out);

        JsonObject root = JsonParser.parseString(Files.readString(out)).getAsJsonObject();
        int written = 0;
        for (String category : root.keySet()) {
            assertTrue(root.get(category).isJsonObject(), category + " is not a category object");
            written += root.getAsJsonObject(category).size();
        }
        assertEquals(ConfigSchema.all().size(), written,
                "every schema option must reach the file exactly once");
    }

    @Test
    void leavesNoTempFileBehind() throws Exception {
        ConfigWriter.save(dir.resolve("lethalbreed.json"));
        assertFalse(Files.exists(dir.resolve("lethalbreed.json.tmp")),
                "the write-then-rename must not leave its scratch file");
    }

    @Test
    void categoriesAreSortedSoRepeatedWritesDoNotChurn() throws Exception {
        Path out = dir.resolve("lethalbreed.json");
        ConfigWriter.save(out);
        String first = Files.readString(out);
        ConfigWriter.save(out);
        assertEquals(first, Files.readString(out),
                "the file is rewritten on every launch; a non-deterministic order would diff every run");
    }

    @Test
    void overwritesAnExistingFileInPlace() throws Exception {
        Path out = dir.resolve("lethalbreed.json");
        Files.writeString(out, "{\"stale\": {}}");
        ConfigWriter.save(out);
        assertFalse(Files.readString(out).contains("stale"));
    }
}
