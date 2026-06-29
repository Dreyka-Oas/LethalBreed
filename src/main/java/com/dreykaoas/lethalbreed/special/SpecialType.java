package com.dreykaoas.lethalbreed.special;

import java.util.ArrayList;
import java.util.List;

/**
 * Special zombie variants. Each spawned zombie may roll ONE of these (chance + phase, see
 * {@link SpecialRoller}); the choice is stored as a persistent Fabric attachment and read by the
 * {@code SmartZombie}. {@link Kind} decides where the behaviour lives:
 * PASSIVE = spawn-time buffs only; ACTIVE = per-tick action ({@link SpecialBehavior#tick}); DEATH = on death.
 */
public enum SpecialType {
    NONE("none", "", Kind.PASSIVE, 0, 0),
    SPRINTEUR("sprinteur", "Sprinteur", Kind.PASSIVE, 2, 10),
    TOXIQUE("toxique", "Toxique", Kind.ACTIVE, 2, 10),
    BONDISSEUR("bondisseur", "Bondisseur", Kind.PASSIVE, 3, 9),
    CRACHEUR("cracheur", "Cracheur", Kind.ACTIVE, 3, 9),
    BOMBEUR("bombeur", "Bombeur", Kind.ACTIVE, 4, 7),
    GIVRE("givre", "Givré", Kind.ACTIVE, 4, 8),
    HURLEUR("hurleur", "Hurleur", Kind.ACTIVE, 5, 7),
    SOIGNEUR("soigneur", "Soigneur", Kind.ACTIVE, 6, 6),
    JUGGERNAUT("juggernaut", "Juggernaut", Kind.PASSIVE, 6, 6),
    FOUISSEUR("fouisseur", "Fouisseur", Kind.PASSIVE, 8, 5),
    NECROMANCIEN("necromancien", "Nécromancien", Kind.ACTIVE, 9, 4),
    SPLITTER("splitter", "Splitter", Kind.DEATH, 11, 4);

    public enum Kind { PASSIVE, ACTIVE, DEATH }

    private final String id;
    private final String frName;
    private final Kind kind;
    private final int unlockPhase;
    private final int weight;

    SpecialType(String id, String frName, Kind kind, int unlockPhase, int weight) {
        this.id = id;
        this.frName = frName;
        this.kind = kind;
        this.unlockPhase = unlockPhase;
        this.weight = weight;
    }

    public String id() { return id; }
    public String frName() { return frName; }
    public Kind kind() { return kind; }
    public int unlockPhase() { return unlockPhase; }
    public int weight() { return weight; }

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
            if (t != NONE && phase >= t.unlockPhase) {
                out.add(t);
            }
        }
        return out;
    }
}
