package com.dreykaoas.lethalbreed.config.io;

import com.dreykaoas.lethalbreed.config.schema.ConfigSchema;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check of the rename migration against a config file in the shape a real player would have on
 * disk before the update — nested under the old French category, with French option names.
 *
 * <p>{@link ConfigLegacyNamesTest} pins the table itself; this pins the thing that actually matters, which
 * is that {@code ConfigStructure} turns those stale keys into {@link ConfigStructure.Rename} entries.
 * {@code ConfigLoader} then moves each value onto its new key before the field-driven apply loop runs, so
 * a rename that produces no Rename record is a silently reset option.
 */
class ConfigLegacyMigrationTest {

    private static Set<String> knownNames() {
        Set<String> known = new HashSet<>();
        for (Field f : ConfigSchema.all()) {
            known.add(f.getName());
        }
        return known;
    }

    /** The pre-update file: old category, old names, values the player chose. */
    private static final String OLD_CONFIG = """
            {
              "Specials": {
                "specialHurleurRadius": 42.0,
                "specialBombeurPhase": 7,
                "specialSoigneurRegenTicks": 111,
                "specialBondisseurLeapAmp": 3
              },
              "Meute": {
                "packMaxSize": 11
              }
            }
            """;

    @Test
    void everyStaleFrenchKeyIsRepairedOntoItsEnglishName() {
        JsonObject root = JsonParser.parseString(OLD_CONFIG).getAsJsonObject();
        ConfigStructure.Report report = ConfigStructure.check(root, knownNames());

        Map<String, String> repaired = report.renamed().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ConfigStructure.Rename::from, ConfigStructure.Rename::to));

        assertEquals("specialScreamerRadius", repaired.get("specialHurleurRadius"));
        assertEquals("specialBomberPhase", repaired.get("specialBombeurPhase"));
        assertEquals("specialHealerRegenTicks", repaired.get("specialSoigneurRegenTicks"));
        assertEquals("specialLeaperLeapAmp", repaired.get("specialBondisseurLeapAmp"));
    }

    /** Nothing may be reported as lost: an entry in {@code unknown} is a value the player silently loses. */
    @Test
    void noPlayerValueIsDropped() {
        JsonObject root = JsonParser.parseString(OLD_CONFIG).getAsJsonObject();
        ConfigStructure.Report report = ConfigStructure.check(root, knownNames());

        assertTrue(report.unknown().isEmpty(),
                "these options would be reset to default instead of migrated: " + report.unknown());
        assertTrue(report.duplicated().isEmpty(), "unexpected duplicates: " + report.duplicated());
    }

    /** Renaming the category is self-healing rather than destructive: "Meute" is no longer a known category,
     *  but packMaxSize inside it is still recognised by name, kept, and rewritten under "Pack". */
    @Test
    void theRenamedCategoryIsReportedButItsOptionsSurvive() {
        JsonObject root = JsonParser.parseString(OLD_CONFIG).getAsJsonObject();
        ConfigStructure.Report report = ConfigStructure.check(root, knownNames());

        assertTrue(report.bogusCategory().contains("Meute"),
                "the old French category should be flagged once, then rewritten");
        assertTrue(report.unknown().stream().noneMatch(u -> u.name().equals("packMaxSize")),
                "packMaxSize sat inside the renamed category and must not be lost with it");
    }

    /** Every entry in the table must actually fire against a file that uses it — a table entry whose target
     *  is not in the schema is inert, and ConfigStructure would silently fall through to the fuzzy search. */
    @Test
    void everyLegacyNameIsRepairableInPractice() {
        Set<String> known = knownNames();
        JsonObject flat = new JsonObject();
        ConfigLegacyNames.all().keySet().forEach(oldName -> flat.addProperty(oldName, 1));

        ConfigStructure.Report report = ConfigStructure.check(flat, known);

        assertEquals(ConfigLegacyNames.all().size(), report.renamed().size(),
                "every legacy name must be repaired; unrepaired: " + report.unknown());
    }
}
