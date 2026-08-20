package com.dreykaoas.lethalbreed.entity.genes;

import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import com.dreykaoas.lethalbreed.phase.PhaseConfig;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import java.util.Random;

/**
 * What the difficulty phase adds on top of a zombie's own genes: the health, damage and speed multipliers
 * drawn from the phase's widening ranges, and the beneficial effects that come with a high enough tier.
 *
 * <p>Kept apart from {@link ZombieVariation}, which draws the genes themselves. A gene is fixed for the
 * life of the zombie; the phase scaling is a snapshot of the world at the moment it spawned. Two different
 * questions, two different seeds, and the ceilings only ever apply to this half.
 */
final class PhaseScaling {
    private PhaseScaling() {}

    private static final Identifier HP_ID = Identifier.fromNamespaceAndPath("lethalbreed", "phase_hp");
    private static final Identifier PDMG_ID = Identifier.fromNamespaceAndPath("lethalbreed", "phase_dmg");
    private static final Identifier PSPD_ID = Identifier.fromNamespaceAndPath("lethalbreed", "phase_spd");
    private static final long PHASE_SALT = 91237L;

    /**
     * Scale a freshly-spawned zombie by the CURRENT difficulty phase: extra HP/damage/speed (rolled from the
     * phase's widening ranges) and phase-scaled effects. Seeded by UUID (distinct salt)
     * so a zombie's build is stable. The HP modifier is a permanent attribute modifier; refill to full so the
     * bigger pool isn't left half-empty.
     */
    static void apply(Zombie z) {
        PhaseConfig.PhaseDef p = PhaseConfig.def(PhaseManager.current());
        Random r = ZombieVariation.seeded(z, PHASE_SALT);
        ZombieVariation.applyMultiplier(z, Attributes.MAX_HEALTH, HP_ID, ZombieVariation.roll(r, p.hpMin(), p.hpMax()));
        ZombieVariation.applyMultiplier(z, Attributes.ATTACK_DAMAGE, PDMG_ID, ZombieVariation.roll(r, p.dmgMin(), p.dmgMax()));
        ZombieVariation.applyMultiplier(z, Attributes.MOVEMENT_SPEED, PSPD_ID, ZombieVariation.roll(r, p.spdMin(), p.spdMax()));
        z.setHealth(z.getMaxHealth());
        applyEffects(z, r, p);
    }

    /** Apply {@code effCount} beneficial effects (chance-gated) from the pool, amplifier up to the phase max.
     *  Gated by the master {@link WorldSpawnConfig#randomEffectEnabled} switch (so it disables BOTH paths) and
     *  hard-capped by the global {@link WorldSpawnConfig#randomEffectMaxAmplifier} ceiling. */
    private static void applyEffects(Zombie z, Random r, PhaseConfig.PhaseDef p) {
        if (!WorldSpawnConfig.randomEffectEnabled) {
            return;
        }
        if (p.effChance() <= 0 || p.effCount() <= 0 || r.nextDouble() >= p.effChance()) {
            return;
        }
        int maxAmp = Math.min(WorldSpawnConfig.randomEffectMaxAmplifier, p.effMaxAmp());
        Holder<MobEffect>[] pool = ZombieVariation.effectPool();
        for (int i = 0; i < p.effCount(); i++) {
            Holder<MobEffect> pick = pool[r.nextInt(pool.length)];
            // Math.max(1,..) guards nextInt against a 0/negative bound (mirrors applyRandomEffect): a phase
            // with effMaxAmp 0 still rolls amp 0, never throws IllegalArgumentException.
            int amp = r.nextInt(Math.max(1, maxAmp + 1));
            LethalBreedEffects.applyInfinite(z, pick, amp);
        }
    }

}
