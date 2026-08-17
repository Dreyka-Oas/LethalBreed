package com.dreykaoas.lethalbreed.special;

import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Special zombie variants. Each spawned zombie may roll ONE of these (chance + phase, see
 * {@link SpecialRoller}); the choice is stored as a persistent Fabric attachment and read by the
 * {@code SmartZombie}. {@link Kind} decides where the behaviour lives:
 * PASSIVE = spawn-time buffs only; ACTIVE = per-tick action ({@link SpecialBehavior#tick}); DEATH = on death.
 *
 * <p>The {@linkplain #unlockPhase() unlock phase} (from which phase a type can appear) and the
 * {@linkplain #weight() selection weight} (relative frequency) are NOT hard-coded here — they are read live
 * from {@link ProgressionConfig} ({@code special<Type>Phase} / {@code special<Type>Weight}), so both are
 * editable through the config JSON / GUI / {@code /lethalconfig}. The switches below are only the routing to
 * those config fields; the field defaults hold the built-in values.
 *
 * <p>The names are English, matching what {@code en_us.json} has always displayed; the French names players
 * used to see are now a translation, not a hard-coded string. See {@link #translationKey()}.
 */
public enum SpecialType {
    NONE("none", Kind.PASSIVE),
    SPRINTER("sprinter", Kind.PASSIVE),
    LEAPER("leaper", Kind.PASSIVE),
    BOMBER("bomber", Kind.ACTIVE),
    SCREAMER("screamer", Kind.ACTIVE),
    HEALER("healer", Kind.ACTIVE),
    JUGGERNAUT("juggernaut", Kind.PASSIVE),
    NECROMANCER("necromancer", Kind.ACTIVE),
    SPLITTER("splitter", Kind.DEATH);

    public enum Kind { PASSIVE, ACTIVE, DEATH }

    /** Ids written into saves before the vocabulary was translated to English.
     *
     *  <p>Read-only, and permanently so: the {@code lethalbreed:special} attachment is persistent, so every
     *  special zombie already alive in a saved world carries one of these strings. {@link #fromId} accepts
     *  them, {@link #id()} never returns one — which means a world quietly re-saves itself onto the English
     *  ids as its chunks cycle, without a migration pass and without a moment where a Bomber reads back as
     *  a plain zombie. */
    private static final Map<String, SpecialType> LEGACY_IDS = Map.of(
            "sprinteur", SPRINTER,
            "bondisseur", LEAPER,
            "bombeur", BOMBER,
            "hurleur", SCREAMER,
            "soigneur", HEALER,
            "necromancien", NECROMANCER);

    private final String id;
    private final Kind kind;

    SpecialType(String id, Kind kind) {
        this.id = id;
        this.kind = kind;
    }

    public String id() { return id; }
    public Kind kind() { return kind; }

    /** Translation key for the name shown on the entity.
     *
     *  <p>Replaces the old {@code frName()}, whose value reached every player through
     *  {@code Component.literal} — so an English client saw a zombie labelled « Nécromancien ». Both lang
     *  files carry these keys, so each player now gets the name in their own language. */
    public String translationKey() { return "lethalbreed.special." + id; }

    /** Phase from which this type can appear — configurable via {@link ProgressionConfig}. */
    public int unlockPhase() {
        return switch (this) {
            case SPRINTER -> SpecialVariantConfig.specialSprinteurPhase;
            case LEAPER -> SpecialVariantConfig.specialBondisseurPhase;
            case BOMBER -> SpecialVariantConfig.specialBombeurPhase;
            case SCREAMER -> SpecialVariantConfig.specialHurleurPhase;
            case HEALER -> SpecialVariantConfig.specialSoigneurPhase;
            case JUGGERNAUT -> SpecialVariantConfig.specialJuggernautPhase;
            case NECROMANCER -> SpecialVariantConfig.specialNecromancienPhase;
            case SPLITTER -> SpecialVariantConfig.specialSplitterPhase;
            case NONE -> 0;
        };
    }

    /** Relative selection weight (higher = more frequent; 0 = never picked) — configurable via {@link ProgressionConfig}. */
    public int weight() {
        return switch (this) {
            case SPRINTER -> SpecialVariantConfig.specialSprinteurWeight;
            case LEAPER -> SpecialVariantConfig.specialBondisseurWeight;
            case BOMBER -> SpecialVariantConfig.specialBombeurWeight;
            case SCREAMER -> SpecialVariantConfig.specialHurleurWeight;
            case HEALER -> SpecialVariantConfig.specialSoigneurWeight;
            case JUGGERNAUT -> SpecialVariantConfig.specialJuggernautWeight;
            case NECROMANCER -> SpecialVariantConfig.specialNecromancienWeight;
            case SPLITTER -> SpecialVariantConfig.specialSplitterWeight;
            case NONE -> 0;
        };
    }

    public static SpecialType fromId(String id) {
        if (id == null || id.isEmpty()) {
            return NONE;
        }
        for (SpecialType t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return LEGACY_IDS.getOrDefault(id, NONE);
    }

    /** The highest unlock phase any type asks for — i.e. the phase at which every type is available.
     *  Used when the phase system is switched off: with no progression there is no reason to keep content
     *  permanently locked, so the roll behaves as though everything had been unlocked. */
    public static int maxUnlockPhase() {
        int max = 0;
        for (SpecialType t : values()) {
            if (t != NONE) {
                max = Math.max(max, t.unlockPhase());
            }
        }
        return max;
    }

    /** Types unlocked at or below the given phase (excludes NONE). */
    public static List<SpecialType> available(int phase) {
        List<SpecialType> out = new ArrayList<>();
        for (SpecialType t : values()) {
            if (t != NONE && phase >= t.unlockPhase()) {
                out.add(t);
            }
        }
        return out;
    }
}
