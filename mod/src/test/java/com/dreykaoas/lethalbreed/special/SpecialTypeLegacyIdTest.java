package com.dreykaoas.lethalbreed.special;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The special id is a PERSISTENT entity attachment: every special zombie already alive in a saved world
 * carries the French id in its NBT. {@code fromId} must keep resolving those, or a world loaded after the
 * update finds a horde of plain zombies where its Bombeurs used to be.
 *
 * <p>The reverse does not need an alias: {@code id()} only ever returns the current name, so a world
 * re-saves itself onto the English ids on its own, one chunk at a time.
 */
class SpecialTypeLegacyIdTest {

    @Test
    void resolvesTheCurrentEnglishIds() {
        assertSame(SpecialType.BOMBER, SpecialType.fromId("bomber"));
        assertSame(SpecialType.SCREAMER, SpecialType.fromId("screamer"));
        assertSame(SpecialType.NECROMANCER, SpecialType.fromId("necromancer"));
    }

    @Test
    void stillResolvesTheFrenchIdsWrittenIntoExistingSaves() {
        assertSame(SpecialType.SPRINTER, SpecialType.fromId("sprinteur"));
        assertSame(SpecialType.LEAPER, SpecialType.fromId("bondisseur"));
        assertSame(SpecialType.BOMBER, SpecialType.fromId("bombeur"));
        assertSame(SpecialType.SCREAMER, SpecialType.fromId("hurleur"));
        assertSame(SpecialType.HEALER, SpecialType.fromId("soigneur"));
        assertSame(SpecialType.NECROMANCER, SpecialType.fromId("necromancien"));
    }

    @Test
    void writesOnlyTheEnglishIdBack() {
        assertEquals("bomber", SpecialType.BOMBER.id());
        assertEquals("screamer", SpecialType.SCREAMER.id());
    }

    @Test
    void unknownIdIsStillNone() {
        assertSame(SpecialType.NONE, SpecialType.fromId("nope"));
        assertSame(SpecialType.NONE, SpecialType.fromId(null));
        assertSame(SpecialType.NONE, SpecialType.fromId(""));
    }

    @Test
    void everyTypeHasATranslationKey() {
        for (SpecialType t : SpecialType.values()) {
            if (t != SpecialType.NONE) {
                assertEquals("lethalbreed.special." + t.id(), t.translationKey());
            }
        }
    }
}
