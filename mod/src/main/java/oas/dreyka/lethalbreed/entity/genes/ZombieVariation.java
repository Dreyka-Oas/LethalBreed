package oas.dreyka.lethalbreed.entity.genes;

import oas.dreyka.lethalbreed.config.domain.engine.ExpertConfig;
import oas.dreyka.lethalbreed.config.domain.ProgressionConfig;
import oas.dreyka.lethalbreed.config.domain.WorldSpawnConfig;

import oas.dreyka.lethalbreed.effect.LethalBreedEffects;
import oas.dreyka.lethalbreed.entity.spawn.SpawnControl;
import oas.dreyka.lethalbreed.phase.PhaseManager;
import oas.dreyka.lethalbreed.util.AttributeModifiers;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.Random;

/**
 * Gives each zombie a modest, individual flavour: random size, strength, speed and leap power. Rolls
 * are seeded by the entity UUID so they are deterministic (stable across reloads) and applied via
 * permanent attribute modifiers with fixed ids (idempotent, no compounding on chunk reload).
 */
public final class ZombieVariation {
    private ZombieVariation() {}

    private static final Identifier SCALE_ID = Identifier.fromNamespaceAndPath("lethalbreed", "rand_scale");
    private static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath("lethalbreed", "rand_speed");
    private static final Identifier DAMAGE_ID = Identifier.fromNamespaceAndPath("lethalbreed", "rand_damage");
    private static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath("lethalbreed", "rand_health");

    private static final long EFFECT_SALT = 4242L;
    private static final long LEAP_SALT = 777L;

    /** Distinct salt so the special roll does not consume the phase RNG: sharing {@code r} inside applyPhase
     *  would couple a zombie's variant to how many phase rolls happened before it. */
    private static final long SPECIAL_SALT = 55501L;

    public static void apply(Zombie z) {
        // Same option EntityTrackingInit reads on load. Stripping here too is not redundant work: this pass
        // runs inside finalizeSpawn, before the zombie is in the world, so it never gets to exist holding the
        // gear vanilla just handed it.
        if (WorldSpawnConfig.stripZombieEquipment) {
            SpawnControl.stripEquipment(z); // also clears vanilla natural gear and pickups
        }
        if (WorldSpawnConfig.enableVariation) {
            Random r = seeded(z, 0L);
            applyMultiplier(z, Attributes.SCALE, SCALE_ID, roll(r, WorldSpawnConfig.varScaleMin, WorldSpawnConfig.varScaleMax));
            applyMultiplier(z, Attributes.MOVEMENT_SPEED, SPEED_ID, roll(r, WorldSpawnConfig.varSpeedMin, WorldSpawnConfig.varSpeedMax));
            applyMultiplier(z, Attributes.ATTACK_DAMAGE, DAMAGE_ID, roll(r, WorldSpawnConfig.varDamageMin, WorldSpawnConfig.varDamageMax));
            applyMultiplier(z, Attributes.MAX_HEALTH, HEALTH_ID, roll(r, WorldSpawnConfig.varHealthMin, WorldSpawnConfig.varHealthMax));
            z.setHealth(z.getMaxHealth()); // refill so the resized pool isn't left partly empty
        }
        if (ProgressionConfig.phaseSystemEnabled) {
            PhaseScaling.apply(z); // phase scaling drives stats/gear/effects
        } else {
            applyRandomEffect(z); // legacy flat effect roll when the phase system is off
        }
        // Outside the branch on purpose. Inside applyPhase the roll would be gated on phaseSystemEnabled, a
        // legitimate exposed option, so turning it off would silently disable every special variant for the
        // life of the world while some forty special* options stayed visible and editable in the GUI with
        // nothing to act on. With the phase system off there is no phase to gate unlocks, so the roll runs at
        // the floor phase where only the earliest types are available.
        oas.dreyka.lethalbreed.special.SpecialRoller.roll(z, seeded(z, SPECIAL_SALT),
                ProgressionConfig.phaseSystemEnabled
                        ? PhaseManager.current()
                        // The registry, not the enum: an addon's variant asking for a later phase than any
                        // of the eight would otherwise never be reachable on a server with phases off.
                        : oas.dreyka.lethalbreed.api.variant.SpecialVariantRegistry.maxUnlockPhase());
        // LAST, and it has to stay last: it reads finished attribute values, so every modifier and effect
        // above, including the Juggernaut's health multiplier and the Sprinter's speed, must already be
        // stamped. Moving this call earlier would let exactly those escape the cap.
        AttributeCaps.enforce(z);
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
        LethalBreedEffects.applyInfinite(z, pick, amp);
    }

    /** Beneficial effects useful to a hunting zombie (chase / damage / tank / dig) + custom LEAP. No
     *  FIRE_RESISTANCE: every zombie must burn in daylight, so a sun-immunity buff is excluded on purpose.
     *
     *  <p>No REGENERATION either, and not as a balance call: vanilla's {@code canBeAffected} rejects it for
     *  everything tagged {@code ignores_poison_and_regen}, which covers {@code #undead}. Leaving it in the
     *  pool meant roughly one buff roll in nine silently did nothing, and since the roll is seeded on the
     *  zombie's UUID, the same zombie drew the same blank every time its chunk reloaded. */
    @SuppressWarnings("unchecked")
    static Holder<MobEffect>[] effectPool() {
        return new Holder[] {
                MobEffects.SPEED, MobEffects.STRENGTH, MobEffects.RESISTANCE,
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

    static void applyMultiplier(LivingEntity e, Holder<Attribute> attr, Identifier id, double factor) {
        // Floor SCALE/SPEED so an extreme low roll can't make a zombie invisibly tiny or frozen in place.
        if (attr == Attributes.SCALE || attr == Attributes.MOVEMENT_SPEED) {
            factor = Math.max(ExpertConfig.expertAttributeFloor, factor);
        }
        AttributeModifiers.multiplyBase(e, attr, id, factor);
    }

    static Random seeded(Zombie z, long salt) {
        return new Random(z.getUUID().getMostSignificantBits() ^ z.getUUID().getLeastSignificantBits() ^ salt);
    }

    static double roll(Random r, double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            return 1.0; // non-finite range → neutral factor (no resize)
        }
        double lo = Math.min(min, max);
        double hi = Math.max(min, max);
        return lo + r.nextDouble() * (hi - lo);
    }
}
