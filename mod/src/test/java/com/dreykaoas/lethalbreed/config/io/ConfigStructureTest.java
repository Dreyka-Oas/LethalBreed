package com.dreykaoas.lethalbreed.config.io;

import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless characterization of {@link ConfigStructure} — the shape check for the config file.
 *
 * <p>This logic lives outside {@link ConfigIo} precisely so it can be tested: ConfigIo imports
 * FabricLoader and the mod entrypoint's LOGGER, so it cannot load under plain JUnit. Same reason
 * {@code ContaminationRoll} and {@code PlacedBlockPolicy} were extracted.
 *
 * <p>The check reports SHAPE, never values: a user is free to set any number they like, and an
 * out-of-range value is clamped elsewhere by design. What it looks for is a file whose keys no
 * longer correspond to the options the mod actually has.
 */
class ConfigStructureTest {

    /** A deliberately tiny stand-in for the real 305 options — the check takes the name set as a
     *  parameter so these tests never depend on the live schema, which changes every release. */
    private static final Set<String> KNOWN = Set.of("tickBuckets", "daySleepEnabled", "lodHigh");

    private static JsonObject parse(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void aWellFormedNestedFileIsClean() {
        ConfigStructure.Report r = ConfigStructure.check(parse(
                "{\"Perf\":{\"tickBuckets\":5,\"lodHigh\":40.0},\"Mood\":{\"daySleepEnabled\":true}}"), KNOWN);
        assertTrue(r.clean(), "a correct file must raise nothing");
        assertEquals(3, r.recognised());
        assertFalse(r.unusable());
    }

    @Test
    void aLegacyFlatFileIsAlsoClean() {
        // The flat layout predates the category migration, so it is what every pre-migration user has
        // on disk. Reporting it as broken would tell all of them their config is corrupt on first
        // launch — and would undo the whole point of the tolerant reader in ConfigIo.load().
        ConfigStructure.Report r = ConfigStructure.check(parse(
                "{\"tickBuckets\":5,\"daySleepEnabled\":true,\"lodHigh\":40.0}"), KNOWN);
        assertTrue(r.clean(), "a legacy flat file is old, not broken");
        assertEquals(3, r.recognised());
    }

    @Test
    void aMisspelledKeySuggestsTheClosestRealOption() {
        // The headline case: today such an edit does nothing AND the line is silently deleted on the
        // next save, so the user watches their edit vanish with no explanation.
        ConfigStructure.Report r = ConfigStructure.check(parse("{\"Perf\":{\"tickBucket\":5}}"), KNOWN);
        assertEquals(1, r.unknown().size());
        assertEquals("tickBucket", r.unknown().get(0).name());
        assertEquals("tickBuckets", r.unknown().get(0).suggestion());
    }

    @Test
    void anUnrelatedKeyGetsNoSuggestion() {
        ConfigStructure.Report r = ConfigStructure.check(parse("{\"Perf\":{\"zzzzzzzzzzzz\":5}}"), KNOWN);
        assertEquals(1, r.unknown().size());
        assertNull(r.unknown().get(0).suggestion(), "a wild guess is worse than no guess");
    }

    @Test
    void theSameOptionUnderTwoCategoriesIsReported() {
        // ConfigIo's flatten is last-wins with no collision check, so without this the user silently
        // gets whichever copy happened to be iterated last.
        ConfigStructure.Report r = ConfigStructure.check(parse(
                "{\"Perf\":{\"tickBuckets\":5},\"Misc\":{\"tickBuckets\":9}}"), KNOWN);
        assertEquals(List.of("tickBuckets"), r.duplicated());
    }

    @Test
    void anUnknownCategoryNameIsReported() {
        ConfigStructure.Report r = ConfigStructure.check(parse("{\"Perff\":{\"tickBuckets\":5}}"), KNOWN);
        assertEquals(List.of("Perff"), r.bogusCategory());
    }

    @Test
    void anOptionInTheWrongCategoryIsReportedButStillCounted() {
        // Tolerated and auto-corrected by save(), so this is drift worth surfacing, not an error.
        ConfigStructure.Report r = ConfigStructure.check(parse("{\"Mood\":{\"tickBuckets\":5}}"), KNOWN);
        assertEquals(List.of("tickBuckets"), r.misplaced());
        assertEquals(1, r.recognised(), "a misplaced option is still read");
    }

    @Test
    void aFileWithNoRecognisedOptionIsUnusable() {
        ConfigStructure.Report r = ConfigStructure.check(parse(
                "{\"Nonsense\":{\"alpha\":1,\"beta\":2}}"), KNOWN);
        assertTrue(r.unusable(), "content present but nothing recognised is the corruption signal");
    }

    @Test
    void anEmptyObjectIsNotUnusable() {
        // Nothing to lose and nothing wrong: save() simply writes the full defaults.
        assertFalse(ConfigStructure.check(parse("{}"), KNOWN).unusable());
    }
}
