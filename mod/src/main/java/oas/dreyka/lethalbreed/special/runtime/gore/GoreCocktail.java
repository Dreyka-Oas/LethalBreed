package oas.dreyka.lethalbreed.special.runtime.gore;

import oas.dreyka.lethalbreed.config.domain.special.BomberConfig;
import oas.dreyka.lethalbreed.special.runtime.BomberBlast;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The random cocktail of afflictions one Bomber carries. Rolled once when it bursts and then reused for
 * every victim of that blast and for the puddle it leaves, so a given Bomber has a recognisable poison
 * rather than a new surprise per victim.
 *
 * <p>Every effect in the pool is a hindrance that cannot kill on its own: Poison famously stops at half a
 * heart, and the rest only slow, blind, weaken or starve. That is a deliberate boundary: the Bomber is meant
 * to make the horde around you lethal, not to be lethal itself, and it keeps the cocktail from interacting
 * with the no-one-shot guarantee that {@code AttributeCaps} enforces on the zombies.
 *
 * <p>The pool lives here, outside {@link BomberBlast}, because {@code Holder<MobEffect>} is a Minecraft
 * type, and BomberBlast's freedom from those imports is what lets its maths be unit-tested without booting a
 * server. Only the counts and amplifiers (the parts worth testing) live there.
 */
public final class GoreCocktail {
    private GoreCocktail() {}

    /**
     * How long a family of afflictions lasts. Read through the enum rather than stored in the pool, because
     * the pool is built once at class-init and the durations are config options: a captured {@code double}
     * would freeze whatever was in the file the first time a Bomber burst.
     */
    private enum Curve {
        /** Hampers: poison, slowness, weakness, mining fatigue. */
        HAMPER,
        /** Disorients: nausea, hunger. Longer, because losing your bearings for three seconds is nothing. */
        DISORIENT,
        /** Blindness, short on purpose: it takes sight, not capability. */
        BLIND;

        double baseS() {
            return switch (this) {
                case HAMPER -> BomberConfig.specialBomberDoseBaseSec;
                case DISORIENT -> BomberConfig.specialBomberLongDoseBaseSec;
                case BLIND -> BomberConfig.specialBomberBlindDoseBaseSec;
            };
        }

        double spanS() {
            return switch (this) {
                case HAMPER -> BomberConfig.specialBomberDoseSpanSec;
                case DISORIENT -> BomberConfig.specialBomberLongDoseSpanSec;
                case BLIND -> BomberConfig.specialBomberBlindDoseSpanSec;
            };
        }
    }

    /**
     * One entry of the pool.
     *
     * @param effect     what to apply
     * @param curve      which duration family it belongs to
     * @param capped     whether the amplifier may rise to {@code specialBomberDoseAmpCap}. False pins it at 0,
     *                   for effects whose amplifier does nothing in vanilla: showing the player "Nausea III"
     *                   would promise a severity the game does not implement.
     * @param blindGated whether this entry is subject to {@code specialBomberBlindThreshold}
     */
    private record Entry(Holder<MobEffect> effect, Curve curve, boolean capped, boolean blindGated) {}

    /** One rolled affliction: an effect and the amplifier this Bomber drew for it. */
    public record Dose(Holder<MobEffect> effect, int amplifier, double baseS, double spanS) {}

    private static final List<Entry> POOL = List.of(
            new Entry(MobEffects.NAUSEA, Curve.DISORIENT, false, false),
            new Entry(MobEffects.POISON, Curve.HAMPER, true, false),
            new Entry(MobEffects.SLOWNESS, Curve.HAMPER, true, false),
            new Entry(MobEffects.WEAKNESS, Curve.HAMPER, true, false),
            new Entry(MobEffects.MINING_FATIGUE, Curve.HAMPER, true, false),
            new Entry(MobEffects.HUNGER, Curve.DISORIENT, true, false),
            // Blindness stays behind its threshold, keeping specialBomberBlindThreshold's documented meaning
            // ("intensity from which Blindness is applied", 1.0 disables it). It takes away information
            // where the rest of the pool takes away capability, so it alone is gated.
            new Entry(MobEffects.BLINDNESS, Curve.BLIND, false, true));

    /**
     * Roll this Bomber's cocktail.
     *
     * <p>Drawn WITHOUT replacement. Drawing with replacement (the pattern {@code ZombieVariation} uses for
     * beneficial buffs) would routinely collapse "four effects" into one or two, since a repeat draw only
     * overwrites the same effect. Distinct afflictions are what this method promises.
     *
     * @param phase     current difficulty phase; drives how many effects and how strong they may be
     * @param intensity the blast's intensity at its centre, used only to decide Blindness eligibility
     */
    public static List<Dose> roll(int phase, double intensity, RandomSource rng) {
        List<Entry> eligible = new ArrayList<>(POOL.size());
        boolean blindOk = GoreCurves.blindnessEligible(intensity);
        for (Entry e : POOL) {
            if (!e.blindGated() || blindOk) {
                eligible.add(e);
            }
        }
        int want = Math.min(GoreCurves.cocktailSize(phase), eligible.size());
        int maxAmp = GoreCurves.cocktailMaxAmp(phase);

        List<Dose> out = new ArrayList<>(want);
        for (int i = 0; i < want; i++) {
            // Swap-remove: draws without replacement in O(1) without shuffling the shared pool.
            Entry picked = eligible.remove(rng.nextInt(eligible.size()));
            int cap = picked.capped() ? Math.max(0, BomberConfig.specialBomberDoseAmpCap) : 0;
            int amp = Math.min(cap, rng.nextInt(Math.max(1, maxAmp + 1)));
            out.add(new Dose(picked.effect(), amp, picked.curve().baseS(), picked.curve().spanS()));
        }
        return out;
    }

    /**
     * Apply one cocktail to one victim at the given intensity. Durations still scale with intensity exactly
     * as before, so distance and fuse length keep mattering; only WHICH effects land is now random.
     */
    public static void apply(LivingEntity victim, List<Dose> cocktail, double intensity) {
        for (Dose d : cocktail) {
            int ticks = GoreCurves.effectTicks(d.baseS(), d.spanS(), intensity);
            if (ticks > 0) {
                victim.addEffect(new MobEffectInstance(d.effect(), ticks, d.amplifier()));
            }
        }
    }
}
