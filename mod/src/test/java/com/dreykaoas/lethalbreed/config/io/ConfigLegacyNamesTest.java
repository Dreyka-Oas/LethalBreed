package com.dreykaoas.lethalbreed.config.io;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    @Disabled("table filled in Task 4")
    void mapsARenamedOptionToItsNewName() {
        assertEquals("specialScreamerRadius", ConfigLegacyNames.newNameOf("specialHurleurRadius"));
    }
}
