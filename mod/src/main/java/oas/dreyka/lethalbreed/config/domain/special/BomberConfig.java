package oas.dreyka.lethalbreed.config.domain.special;

/**
 * What a Bomber's burst leaves behind: the weight the fuse carries in the blast's intensity, the puddle it
 * drops, and the durations of the afflictions in its gore cocktail.
 *
 * <p>Its own holder rather than more fields on {@code SpecialVariantConfig}, which already carries every
 * special type's phase, weight and ability figures. Every option here is named {@code specialBomber…}, so
 * {@code ConfigCategory} still files them on the Specials tab next to the rest of the Bomber's row.
 */
public final class BomberConfig {
    private BomberConfig() {}

    /** Weight of the fuse in the intensity blend; proximity always keeps the remaining share, so distance
     *  can never stop mattering however long the Bomber swelled. */
    public static double specialBomberFuseWeight = 0.6;

    // ---- The puddle left behind ----
    /** Seconds a puddle lasts at the shortest fuse. */
    public static double specialBomberPuddleBaseSec = 3.0;
    /** Seconds added to a puddle's life at the longest fuse. */
    public static double specialBomberPuddleSpanSec = 9.0;
    /** A puddle is smaller than the ring that made it: the gore settles inward as it lands. */
    public static double specialBomberPuddleRadiusMul = 0.6;
    /** Share of the burst's strength the residue doses at. */
    public static double specialBomberPuddlePotency = 0.5;
    /** How often (ticks) a lingering puddle re-doses whoever stands in it. */
    public static int specialBomberPuddleReapplyTicks = 20;

    // ---- Affliction durations. Two pairs because the cocktail's pool splits in two: the disorienting
    // effects (nausea, hunger) run longer than the ones that merely hamper. Blindness keeps its own short
    // pair, since taking sight away for as long as the rest would be a different effect entirely.
    /** Seconds a hampering affliction (poison, slowness, weakness, mining fatigue) lasts at intensity 0. */
    public static double specialBomberDoseBaseSec = 3.0;
    /** Seconds added to a hampering affliction at full intensity. */
    public static double specialBomberDoseSpanSec = 9.0;
    /** Seconds a disorienting affliction (nausea, hunger) lasts at intensity 0. */
    public static double specialBomberLongDoseBaseSec = 4.0;
    /** Seconds added to a disorienting affliction at full intensity. */
    public static double specialBomberLongDoseSpanSec = 11.0;
    /** Seconds Blindness lasts at intensity 0, once it passes its own threshold. */
    public static double specialBomberBlindDoseBaseSec = 1.0;
    /** Seconds added to Blindness at full intensity. */
    public static double specialBomberBlindDoseSpanSec = 4.0;
    /** Highest amplifier an affliction may reach, whatever the phase allows. Effects whose amplifier does
     *  nothing in vanilla (nausea, blindness) stay at 0 regardless: showing "Nausea III" would promise a
     *  severity the game does not implement. */
    public static int specialBomberDoseAmpCap = 2;
}
