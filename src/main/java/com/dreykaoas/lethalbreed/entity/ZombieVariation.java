package com.dreykaoas.lethalbreed.entity;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;

import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import com.dreykaoas.lethalbreed.phase.PhaseConfig;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import com.dreykaoas.lethalbreed.phase.ZombieEquipper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.Random;

/**
 * Gives each zombie a modest, individual flavour: random size, strength, speed and leap power. Rolls
 * are seeded by the entity UUID so they are deterministic (stable across reloads) and applied via
 * permanent attribute modifiers with fixed ids (idempotent — no compounding on chunk reload).
 */
public final class ZombieVariation {
    private ZombieVariation() {}

    private static final Identifier SCALE_ID = Identifier.fromNamespaceAndPath("lethalbreed", "rand_scale");
    private static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath("lethalbreed", "rand_speed");
    private static final Identifier DAMAGE_ID = Identifier.fromNamespaceAndPath("lethalbreed", "rand_damage");
    private static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath("lethalbreed", "rand_health");

    private static final long EFFECT_SALT = 4242L;
    private static final long LEAP_SALT = 777L;

    private static final Identifier HP_ID = Identifier.fromNamespaceAndPath("lethalbreed", "phase_hp");
    private static final Identifier PDMG_ID = Identifier.fromNamespaceAndPath("lethalbreed", "phase_dmg");
    private static final Identifier PSPD_ID = Identifier.fromNamespaceAndPath("lethalbreed", "phase_spd");
    private static final long PHASE_SALT = 91237L;

    public static void apply(Zombie z) {
        if (WorldSpawnConfig.enableVariation) {
            Random r = seeded(z, 0L);
            applyMultiplier(z, Attributes.SCALE, SCALE_ID, roll(r, WorldSpawnConfig.varScaleMin, WorldSpawnConfig.varScaleMax));
            applyMultiplier(z, Attributes.MOVEMENT_SPEED, SPEED_ID, roll(r, WorldSpawnConfig.varSpeedMin, WorldSpawnConfig.varSpeedMax));
            applyMultiplier(z, Attributes.ATTACK_DAMAGE, DAMAGE_ID, roll(r, WorldSpawnConfig.varDamageMin, WorldSpawnConfig.varDamageMax));
            applyMultiplier(z, Attributes.MAX_HEALTH, HEALTH_ID, roll(r, WorldSpawnConfig.varHealthMin, WorldSpawnConfig.varHealthMax));
            z.setHealth(z.getMaxHealth()); // refill so the resized pool isn't left partly empty
        }
        if (ProgressionConfig.phaseSystemEnabled) {
            applyPhase(z); // phase scaling drives stats/gear/effects
        } else {
            applyRandomEffect(z); // legacy flat effect roll when the phase system is off
        }
    }

    /**
     * Scale a freshly-spawned zombie by the CURRENT difficulty phase: extra HP/damage/speed (rolled from the
     * phase's widening ranges), phase-tiered gear, and phase-scaled effects. Seeded by UUID (distinct salt)
     * so a zombie's build is stable. The HP modifier is a permanent attribute modifier; refill to full so the
     * bigger pool isn't left half-empty.
     */
    private static void applyPhase(Zombie z) {
        PhaseConfig.PhaseDef p = PhaseConfig.def(PhaseManager.current());
        Random r = seeded(z, PHASE_SALT);
        applyMultiplier(z, Attributes.MAX_HEALTH, HP_ID, roll(r, p.hpMin(), p.hpMax()));
        applyMultiplier(z, Attributes.ATTACK_DAMAGE, PDMG_ID, roll(r, p.dmgMin(), p.dmgMax()));
        applyMultiplier(z, Attributes.MOVEMENT_SPEED, PSPD_ID, roll(r, p.spdMin(), p.spdMax()));
        z.setHealth(z.getMaxHealth());
        ZombieEquipper.applyGear(z, r, p);
        applyPhaseEffects(z, r, p);
        com.dreykaoas.lethalbreed.special.SpecialRoller.roll(z, r, PhaseManager.current());
    }

    /** Apply {@code effCount} beneficial effects (chance-gated) from the pool, amplifier up to the phase max.
     *  Gated by the master {@link WorldSpawnConfig#randomEffectEnabled} switch (so it disables BOTH paths) and
     *  hard-capped by the global {@link WorldSpawnConfig#randomEffectMaxAmplifier} ceiling. */
    private static void applyPhaseEffects(Zombie z, Random r, PhaseConfig.PhaseDef p) {
        if (!WorldSpawnConfig.randomEffectEnabled) {
            return;
        }
        if (p.effChance() <= 0 || p.effCount() <= 0 || r.nextDouble() >= p.effChance()) {
            return;
        }
        int maxAmp = Math.min(WorldSpawnConfig.randomEffectMaxAmplifier, p.effMaxAmp());
        Holder<MobEffect>[] pool = effectPool();
        for (int i = 0; i < p.effCount(); i++) {
            Holder<MobEffect> pick = pool[r.nextInt(pool.length)];
            // Math.max(1,..) guards nextInt against a 0/negative bound (mirrors applyRandomEffect): a phase
            // with effMaxAmp 0 still rolls amp 0, never throws IllegalArgumentException.
            int amp = r.nextInt(Math.max(1, maxAmp + 1));
            z.addEffect(new MobEffectInstance(pick, MobEffectInstance.INFINITE_DURATION, amp, false, false, true));
        }
    }

    /**
     * Roll a single random beneficial effect for this zombie (chance-gated), applied at INFINITE duration so
     * it lasts the zombie's whole life. The pool is everything useful to a predator zombie, plus the custom
     * {@link LethalBreedEffects#LEAP}. Seeded by UUID (distinct salt) so a given zombie's "build" is stable;
     * effects are saved in entity NBT, so this once-at-spawn roll persists across chunk reloads.
     */
    private static void applyRandomEffect(Zombie z) {
        if (!WorldSpawnConfig.randomEffectEnabled) {
            return;
        }
        Random r = seeded(z, EFFECT_SALT);
        if (r.nextFloat() >= WorldSpawnConfig.randomEffectChance) {
            return; // this zombie is a plain one
        }
        Holder<MobEffect>[] pool = effectPool();
        Holder<MobEffect> pick = pool[r.nextInt(pool.length)];
        int amp = r.nextInt(Math.max(1, WorldSpawnConfig.randomEffectMaxAmplifier + 1));
        // showParticles=false → no swirling cloud, so players can't read a zombie's buff loadout by sight.
        z.addEffect(new MobEffectInstance(pick, MobEffectInstance.INFINITE_DURATION, amp, false, false, true));
    }

    /** Beneficial effects useful to a hunting zombie (chase / damage / tank / dig) + custom LEAP. No
     *  FIRE_RESISTANCE: every zombie must burn in daylight, so a sun-immunity buff is excluded on purpose. */
    @SuppressWarnings("unchecked")
    private static Holder<MobEffect>[] effectPool() {
        return new Holder[] {
                MobEffects.SPEED, MobEffects.STRENGTH, MobEffects.RESISTANCE, MobEffects.REGENERATION,
                MobEffects.JUMP_BOOST, MobEffects.HASTE, MobEffects.HEALTH_BOOST,
                MobEffects.ABSORPTION, LethalBreedEffects.LEAP,
        };
    }

    /** Deterministic leap-power factor for this zombie. */
    public static double leapFactor(Zombie z) {
        if (!WorldSpawnConfig.enableVariation) {
            return 1.0;
        }
        return roll(seeded(z, LEAP_SALT), WorldSpawnConfig.varLeapMin, WorldSpawnConfig.varLeapMax);
    }

    private static void applyMultiplier(LivingEntity e, Holder<Attribute> attr, Identifier id, double factor) {
        // Floor SCALE/SPEED so an extreme low roll can't make a zombie invisibly tiny or frozen in place.
        if (attr == Attributes.SCALE || attr == Attributes.MOVEMENT_SPEED) {
            factor = Math.max(0.05, factor);
        }
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) {
            inst.addOrReplacePermanentModifier(
                    new AttributeModifier(id, factor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private static Random seeded(Zombie z, long salt) {
        return new Random(z.getUUID().getMostSignificantBits() ^ z.getUUID().getLeastSignificantBits() ^ salt);
    }

    private static double roll(Random r, double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            return 1.0; // non-finite range → neutral factor (no resize)
        }
        double lo = Math.min(min, max);
        double hi = Math.max(min, max);
        return lo + r.nextDouble() * (hi - lo);
    }
}
