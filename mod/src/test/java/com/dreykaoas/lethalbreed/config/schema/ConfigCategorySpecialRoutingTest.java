package com.dreykaoas.lethalbreed.config.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Category routing matches on SUBSTRINGS, so an option's name is not a label here — it is an input to the
 * logic. Renaming one can therefore move it to another GUI tab and, because {@code ConfigWriter} groups by
 * category, to another JSON object in every user's config file, with nothing failing to compile.
 *
 * <p>Two names in the English special-variant vocabulary collide with the Mood rule: {@code Screamer}
 * contains "scream" and {@code HealerRegen*} contains "regen". Both are Specials options and must route
 * there whatever ability word they happen to spell — which is why the {@code special} rule now sits above
 * Contamination and Mood.
 */
class ConfigCategorySpecialRoutingTest {

    @Test
    void everySpecialOptionRoutesToSpecials() {
        for (String name : new String[]{
                "specialScreamerPhase",
                "specialScreamerWeight",
                "specialScreamerRadius",     // contains "scream" -> would hit the Mood rule
                "specialHealerRegenTicks",   // contains "regen"  -> would hit the Mood rule
                "specialHealerRegenAmp",
                "specialBomberPhase",
                "specialBomberInfectChance",
                "specialNecromancerSpread",
                "specialSprinterSpeedMul",
                "specialLeaperLeapAmp"}) {
            assertEquals("Specials", ConfigCategory.of(name), name + " must stay in the Specials tab");
        }
    }

    /** Hoisting the special rule must not steal options from the tabs that legitimately own them. */
    @Test
    void hoistingSpecialDidNotCaptureOtherCategories() {
        assertEquals("Pack", ConfigCategory.of("packMaxSize"));
        assertEquals("Contamination", ConfigCategory.of("contamMaxLevel"));
        assertEquals("Mood", ConfigCategory.of("moodEnabled"));
        assertEquals("Mood", ConfigCategory.of("fleeGroundGainThreshold"));
        assertEquals("Phases", ConfigCategory.of("phaseSystemEnabled"));
    }

    /** The pack rule was renamed from the French "Meute" to "Pack" in the same pass. */
    @Test
    void packCategoryIsEnglish() {
        assertEquals("Pack", ConfigCategory.of("packEnabled"));
        assertEquals("Pack", ConfigCategory.of("packMigrationEnabled"));
    }
}
