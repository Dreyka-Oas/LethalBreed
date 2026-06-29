package com.dreykaoas.lethalbreed.phase;

/**
 * The 15-phase escalation table as data. Each {@link PhaseDef} holds the per-zombie roll RANGES (which widen
 * with phase = "plus haut, plus random") and the gear/effect parameters (which rise = "plus agressif").
 * Tier indices map into {@link ZombieEquipper}'s material ladders (0 = wood/leather … 5 = netherite).
 */
public final class PhaseConfig {
    private PhaseConfig() {}

    public record PhaseDef(
            String name,
            double hpMin, double hpMax,
            double dmgMin, double dmgMax,
            double spdMin, double spdMax,
            double armorChance, int armorMaxTier,
            double weaponChance, int weaponMaxTier,
            int enchantLevel,
            double effChance, int effCount, int effMaxAmp) {}

    public static final PhaseDef[] PHASES = {
            //              name                hp         dmg        spd        armor      weapon     ench eff
            new PhaseDef("Cadaver dormiens", 1.00,1.00, 1.00,1.00, 1.00,1.00, 0.00,0, 0.00,0, 0, 0.00,0,0),
            new PhaseDef("Mortifera vulgaris", 1.00,1.15, 1.00,1.10, 1.00,1.05, 0.10,0, 0.00,0, 0, 0.10,1,0),
            new PhaseDef("Reanimatus gregarius", 1.05,1.25, 1.05,1.15, 1.00,1.10, 0.20,0, 0.05,0, 0, 0.15,1,0),
            new PhaseDef("Putredo errans",   1.10,1.35, 1.10,1.20, 1.00,1.10, 0.35,0, 0.10,0, 0, 0.20,1,1),
            new PhaseDef("Caterva putrescens", 1.20,1.50, 1.15,1.30, 1.05,1.15, 0.50,0, 0.20,1, 0, 0.25,2,1),
            new PhaseDef("Praedator vorax",  1.30,1.70, 1.25,1.45, 1.05,1.20, 0.60,1, 0.30,1, 0, 0.30,2,1),
            new PhaseDef("Miles necroticus", 1.40,1.90, 1.35,1.60, 1.10,1.25, 0.70,2, 0.40,3, 0, 0.35,2,1),
            new PhaseDef("Legio necrotica",  1.50,2.10, 1.45,1.75, 1.10,1.30, 0.80,2, 0.50,3, 0, 0.40,2,2),
            new PhaseDef("Venator pernix",   1.60,2.30, 1.55,1.90, 1.20,1.40, 0.85,3, 0.55,3, 0, 0.50,2,2),
            new PhaseDef("Bestia immanis",   1.80,2.60, 1.80,2.20, 1.15,1.35, 0.90,3, 0.60,3, 0, 0.55,2,2),
            new PhaseDef("Veteranus pestifer", 2.00,2.90, 1.90,2.30, 1.20,1.40, 0.95,3, 0.65,3, 1, 0.60,3,2),
            new PhaseDef("Biodivergence",    2.20,3.20, 2.00,2.50, 1.25,1.45, 1.00,4, 0.70,4, 2, 0.70,3,2),
            new PhaseDef("Tyrannus letalis", 2.40,3.50, 2.20,2.80, 1.30,1.50, 1.00,4, 0.80,4, 3, 0.80,3,3),
            new PhaseDef("Pestis apocalyptica", 2.70,4.00, 2.40,3.00, 1.35,1.55, 1.00,5, 0.90,4, 4, 0.90,3,3),
            new PhaseDef("Necrosis terminalis", 3.00,4.50, 2.50,3.20, 1.40,1.60, 1.00,5, 1.00,5, 5, 1.00,3,3),
    };

    public static PhaseDef def(int phase) {
        int i = Math.max(1, Math.min(PHASES.length, phase)) - 1;
        return PHASES[i];
    }

    public static int count() {
        return PHASES.length;
    }
}
