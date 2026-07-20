package com.dreykaoas.lethalbreed.entity;

/**
 * The ordered roster of render models a plain {@code minecraft:zombie} can wear. Index 0 is the plain
 * {@code vanilla_look} stand-in that ordinary zombies use; 1..15 are the grotesque horror models. The order
 * here IS the wire value stored in {@link HorrorModelAttachment#MODEL} and the index the client renderer keys
 * its model/texture/animation off of, so it must stay in sync with the ids the asset generator emits
 * ({@code mod/tools/models/*.json} + {@code gen_horror_models.py}). These are MODELS, not gameplay "variants"
 * ({@link ZombieVariation} owns the random stat/size variation — a separate concept).
 */
public final class HorrorModels {
    private HorrorModels() {}

    public static final String[] IDS = {
            "vanilla_look",   // 0 — ordinary zombie look
            "ecorche",        // 1  flayed / skinless
            "rampant",        // 2  crawler
            "boursoufle",     // 3  bloated gas-corpse
            "empale",         // 4  impaled / spiked
            "difforme",       // 5  oversized tumour-arm
            "pendu",          // 6  hanged, broken neck
            "colosse",        // 7  towering gutted brute
            "emacie",         // 8  emaciated skeleton
            "brule",          // 9  charred / embered
            "crochu",         // 10 long-fanged predator
            "machoire_brisee",// 11 hanging broken jaw
            "bras_ossature",  // 12 arm stripped to bone
            "crane_eclate",   // 13 burst skull
            "eventre",        // 14 open ribcage, hanging guts
            "traine_bas",     // 15 second crawler, gutted lower half
            "titan",          // 16 colossal fused-flesh mountain
            "echassier",      // 17 very tall, thin, stilt legs
            "nain_tordu",     // 18 small twisted dwarf
            "grouille",       // 19 body swarming with larvae
            "decapite",       // 20 headless, groping
            "siamois",        // 21 conjoined twins, two heads
            "cornu",          // 22 horned / bone antlers
            "noye",           // 23 waterlogged drowned bloat
            "peau_pendante",  // 24 skeleton with sheets of hanging skin
            "tete_enflee",    // 25 huge bulbous head, frail body
            "cul_de_jatte",   // 26 legless, drags on its arms
            "herisse",        // 27 bristling with bone quills
            "brasier",        // 28 actively burning
            "asticot",        // 29 giant grub/maggot crawler
            "araignee",       // 30 spider-like low crawler
    };

    /** Total model count including the vanilla-look stand-in. */
    public static final int COUNT = IDS.length;
    /** Number of horror models (indices 1..HORROR_COUNT). */
    public static final int HORROR_COUNT = COUNT - 1;

    /** Model index for an id, or -1 if unknown. */
    public static int indexOf(String id) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
