package com.dreykaoas.lethalbreed.config.io;

import com.dreykaoas.lethalbreed.config.schema.ConfigSchema;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The legacy table is the only thing between a deliberate option rename and every existing user's tuning
 * being silently reset: {@code ConfigLoader}'s apply loop is field-driven and never looks at a key the
 * schema does not have, and the fuzzy repair in {@code NameSuggest} cannot bridge Hurleur -> Screamer
 * (six edits, on a budget of four).
 */
class ConfigLegacyNamesTest {

    @Test
    void returnsNullForAnUnknownName() {
        assertNull(ConfigLegacyNames.newNameOf("somethingNobodyEverNamed"));
    }

    @Test
    void neverMapsANameOntoItself() {
        ConfigLegacyNames.all().forEach((from, to) ->
                assertNotEquals(from, to, "a legacy entry mapping onto itself is dead weight: " + from));
    }

    @Test
    void mapsARenamedOptionToItsNewName() {
        assertEquals("specialScreamerRadius", ConfigLegacyNames.newNameOf("specialHurleurRadius"));
    }

    @Test
    void coversEveryRenamedSpecialOption() {
        assertEquals(37, ConfigLegacyNames.all().size(),
                "each of the 37 French special-variant options needs a legacy entry");
    }

    /** A legacy entry pointing at an option that no longer exists is silently useless: ConfigStructure
     *  guards on knownNames, so the rename never fires and the value is dropped exactly as if the table
     *  had no entry at all. Only a test catches that. */
    @Test
    void everyLegacyTargetIsARealOption() {
        Set<String> known = new HashSet<>();
        for (Field f : ConfigSchema.all()) {
            known.add(f.getName());
        }
        ConfigLegacyNames.all().forEach((from, to) ->
                assertTrue(known.contains(to),
                        "legacy entry points at an option that does not exist: " + from + " -> " + to));
    }
}
