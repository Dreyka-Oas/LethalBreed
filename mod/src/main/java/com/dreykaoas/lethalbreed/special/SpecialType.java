package com.dreykaoas.lethalbreed.special;

import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;

import java.util.ArrayList;
import java.util.List;

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
 */
public enum SpecialType {
    NONE("none", "", Kind.PASSIVE),
    SPRINTEUR("sprinteur", "Sprinteur", Kind.PASSIVE),
    BONDISSEUR("bondisseur", "Bondisseur", Kind.PASSIVE),
    BOMBEUR("bombeur", "Bombeur", Kind.ACTIVE),
    HURLEUR("hurleur", "Hurleur", Kind.ACTIVE),
    SOIGNEUR("soigneur", "Soigneur", Kind.ACTIVE),
    JUGGERNAUT("juggernaut", "Juggernaut", Kind.PASSIVE),
    NECROMANCIEN("necromancien", "Nécromancien", Kind.ACTIVE),
    SPLITTER("splitter", "Splitter", Kind.DEATH);

    public enum Kind { PASSIVE, ACTIVE, DEATH }

    private final String id;
    private final String frName;
    private final Kind kind;

    SpecialType(String id, String frName, Kind kind) {
        this.id = id;
        this.frName = frName;
        this.kind = kind;
    }

    public String id() { return id; }
    public String frName() { return frName; }
    public Kind kind() { return kind; }

    /** Phase from which this type can appear — configurable via {@link ProgressionConfig}. */
    public int unlockPhase() {
        return switch (this) {
            case SPRINTEUR -> SpecialVariantConfig.specialSprinteurPhase;
            case BONDISSEUR -> SpecialVariantConfig.specialBondisseurPhase;
            case BOMBEUR -> SpecialVariantConfig.specialBombeurPhase;
            case HURLEUR -> SpecialVariantConfig.specialHurleurPhase;
            case SOIGNEUR -> SpecialVariantConfig.specialSoigneurPhase;
            case JUGGERNAUT -> SpecialVariantConfig.specialJuggernautPhase;
            case NECROMANCIEN -> SpecialVariantConfig.specialNecromancienPhase;
            case SPLITTER -> SpecialVariantConfig.specialSplitterPhase;
            case NONE -> 0;
        };
    }

    /** Relative selection weight (higher = more frequent; 0 = never picked) — configurable via {@link ProgressionConfig}. */
    public int weight() {
        return switch (this) {
            case SPRINTEUR -> SpecialVariantConfig.specialSprinteurWeight;
            case BONDISSEUR -> SpecialVariantConfig.specialBondisseurWeight;
            case BOMBEUR -> SpecialVariantConfig.specialBombeurWeight;
            case HURLEUR -> SpecialVariantConfig.specialHurleurWeight;
            case SOIGNEUR -> SpecialVariantConfig.specialSoigneurWeight;
            case JUGGERNAUT -> SpecialVariantConfig.specialJuggernautWeight;
            case NECROMANCIEN -> SpecialVariantConfig.specialNecromancienWeight;
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
        return NONE;
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
