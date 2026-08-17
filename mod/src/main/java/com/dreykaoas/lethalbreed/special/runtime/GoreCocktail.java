package com.dreykaoas.lethalbreed.special.runtime;

import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The random cocktail of afflictions one Bombeur carries. Rolled once when it bursts and then reused for
 * every victim of that blast and for the puddle it leaves, so a given Bombeur has a recognisable poison
 * rather than a new surprise per victim.
 *
 * <p>Every effect in the pool is a hindrance that cannot kill on its own — Poison famously stops at half a
 * heart, and the rest only slow, blind, weaken or starve. That is a deliberate boundary: the Bombeur is meant
 * to make the horde around you lethal, not to be lethal itself, and it keeps the cocktail from interacting
 * with the no-one-shot guarantee that {@code AttributeCaps} enforces on the zombies.
 *
 * <p>The pool lives here rather than in {@link BombeurBlast} because {@code Holder<MobEffect>} is a Minecraft
 * type, and BombeurBlast's freedom from those imports is what lets its maths be unit-tested without booting a
 * server. Only the counts and amplifiers — the parts worth testing — live there.
 */
public final class GoreCocktail {
    private GoreCocktail() {}

    /**
     * One entry of the pool.
     *
     * @param effect     what to apply
     * @param baseS      duration in seconds at intensity 0
     * @param spanS      seconds added at intensity 1
     * @param ampCap     highest amplifier this effect may reach, regardless of the phase. 0 for effects whose
     *                   amplifier does nothing in vanilla — showing the player "Nausea III" would promise a
     *                   severity the game does not implement.
     * @param blindGated whether this entry is subject to {@code specialBomberBlindThreshold}
     */
    private record Entry(Holder<MobEffect> effect, double baseS, double spanS, int ampCap, boolean blindGated) {}

    /** One rolled affliction: an effect and the amplifier this Bombeur drew for it. */
    public record Dose(Holder<MobEffect> effect, int amplifier, double baseS, double spanS) {}

    private static final List<Entry> POOL = List.of(
            new Entry(MobEffects.NAUSEA, 4.0, 11.0, 0, false),
            new Entry(MobEffects.POISON, 3.0, 9.0, 2, false),
            new Entry(MobEffects.SLOWNESS, 3.0, 9.0, 2, false),
            new Entry(MobEffects.WEAKNESS, 3.0, 9.0, 2, false),
            new Entry(MobEffects.MINING_FATIGUE, 3.0, 9.0, 2, false),
            new Entry(MobEffects.HUNGER, 4.0, 11.0, 2, false),
            // Blindness stays behind its threshold, keeping specialBomberBlindThreshold's documented meaning
            // ("intensity from which Blindness is applied", 1.0 disables it). It is also the only entry that
            // takes away information rather than capability, which is why it is the one that is gated.
            new Entry(MobEffects.BLINDNESS, 1.0, 4.0, 0, true));

    /**
     * Roll this Bombeur's cocktail.
     *
     * <p>Drawn WITHOUT replacement. Drawing with replacement — the pattern {@code ZombieVariation} uses for
     * beneficial buffs — would routinely collapse "four effects" into one or two, since a repeat draw only
     * overwrites the same effect. Distinct afflictions are the whole point here.
     *
     * @param phase     current difficulty phase; drives how many effects and how strong they may be
     * @param intensity the blast's intensity at its centre, used only to decide Blindness eligibility
     */
    public static List<Dose> roll(int phase, double intensity, RandomSource rng) {
        List<Entry> eligible = new ArrayList<>(POOL.size());
        boolean blindOk = BombeurBlast.blindnessEligible(intensity);
        for (Entry e : POOL) {
            if (!e.blindGated() || blindOk) {
                eligible.add(e);
            }
        }
        int want = Math.min(BombeurBlast.cocktailSize(phase), eligible.size());
        int maxAmp = BombeurBlast.cocktailMaxAmp(phase);

        List<Dose> out = new ArrayList<>(want);
        for (int i = 0; i < want; i++) {
            // Swap-remove: draws without replacement in O(1) without shuffling the shared pool.
            Entry picked = eligible.remove(rng.nextInt(eligible.size()));
            int amp = Math.min(picked.ampCap(), rng.nextInt(Math.max(1, maxAmp + 1)));
            out.add(new Dose(picked.effect(), amp, picked.baseS(), picked.spanS()));
        }
        return out;
    }

    /**
     * Apply one cocktail to one victim at the given intensity. Durations still scale with intensity exactly
     * as before, so distance and fuse length keep mattering; only WHICH effects land is now random.
     */
    public static void apply(LivingEntity victim, List<Dose> cocktail, double intensity) {
        for (Dose d : cocktail) {
            int ticks = BombeurBlast.effectTicks(d.baseS(), d.spanS(), intensity);
            if (ticks > 0) {
                victim.addEffect(new MobEffectInstance(d.effect(), ticks, d.amplifier()));
            }
        }
    }
}
