package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.config.ConfigOverride;
import com.dreykaoas.lethalbreed.config.domain.DevTestConfig;
import com.dreykaoas.lethalbreed.dev.contam.ContamRig;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.cow.Cow;

/**
 * The plague's damage-over-time contract: health only ever goes DOWN, chips actually fire, an INVERTED
 * configured range ({@code min > max}) still yields a sane chip rather than a negative one that would heal the
 * victim (audit #12), and curing stops the bleed for good.
 *
 * <p>{@code contamMaxLevel} is pinned to 1 for the run so the level-up roll cannot scale the chip past 5 and
 * turn a working evolution mechanic into a false FAIL on the damage draw. Every mutated option is restored by
 * the {@link ConfigOverride} the base opens for the stage — no hand-written {@code savedX} bookkeeping, and
 * the restore now survives an exception thrown mid-run.
 *
 * <p>Shares the "plague" suite with {@link LeakProbeHarness} and {@link PlagueDisableHarness}; the three
 * mutate process-global plague config so they are serialised in time by {@link ContamRig}'s start offsets.
 * Only the disable rig, which runs last, reports the suite summary.
 */
public final class PlagueDamageHarness extends TickPhasedHarness {

    public static final PlagueDamageHarness INSTANCE = new PlagueDamageHarness();

    private static final int CX = 30;
    private static final int CZ = ArenaBuilder.VERIFY_BAND_Z;
    private static final float VICTIM_HEALTH = 1024.0f;
    private static final float EPS = 1.0e-3f;

    private static final int CURE_TICK = 300;
    private static final int POST_CURE_BASELINE_TICK = 310;
    private static final int EVAL_TICK = 400;

    private Cow victim;

    private float startHealth;
    private float maxHealth;
    private float lastHealth;
    private int pulses;
    private float minChip = Float.MAX_VALUE;
    private float maxChip;
    private float healthAtBaseline;
    private boolean cured;

    private PlagueDamageHarness() {
        super("plague", false, new Stage("dot", 0, EVAL_TICK));
    }

    @Override
    protected boolean enabled() {
        return DevTestConfig.devPlagueTest;
    }

    @Override
    protected int startAfterServerTick() {
        return ContamRig.DAMAGE_START;
    }

    @Override
    protected void build(int stage, ServerLevel ow, MinecraftServer server, ConfigOverride cfg) {
        cfg.set("contaminationEnabled", true)
           .set("contamDamageMin", 5.0)   // INVERTED on purpose — min above max (audit #12).
           .set("contamDamageMax", 1.0)
           .set("contamIntervalMinSec", 0.5)
           .set("contamIntervalMaxSec", 0.5)
           .set("contamMaxLevel", 1);

        ContamRig.arena(ow, CX, CZ, 4, 4);
        victim = ContamRig.cow(ow, CX, CZ, VICTIM_HEALTH);
        if (victim == null) {
            return;
        }
        ContaminationManager.contaminate(victim);
        ContaminationManager.forceSymptomatic(victim);
        startHealth = victim.getHealth();
        maxHealth = startHealth;
        lastHealth = startHealth;
    }

    @Override
    protected void observe(int stage, ServerLevel ow, int tick) {
        sample();
        if (tick == CURE_TICK) {
            if (victim != null) {
                ContaminationManager.clearPlague(victim);
            }
            cured = true;
        } else if (tick == POST_CURE_BASELINE_TICK) {
            healthAtBaseline = victim == null ? 0.0f : victim.getHealth();
        }
    }

    private void sample() {
        if (victim == null || victim.isRemoved()) {
            return;
        }
        float h = victim.getHealth();
        if (h > maxHealth) {
            maxHealth = h;
        }
        if (h < lastHealth - EPS && !cured) {
            float chip = lastHealth - h;
            pulses++;
            minChip = Math.min(minChip, chip);
            maxChip = Math.max(maxChip, chip);
        }
        lastHealth = h;
    }

    @Override
    protected void evaluate(int stage, ServerLevel ow, MinecraftServer server) {
        float end = victim == null ? 0.0f : victim.getHealth();
        check("monotone-down", victim != null && maxHealth <= startHealth + EPS,
                "start=" + ContamRig.fmt(startHealth) + " maxEverSeen=" + ContamRig.fmt(maxHealth)
                        + " end=" + ContamRig.fmt(end) + " pulses=" + pulses);

        check("pulses-fired", pulses >= 3,
                pulses + " health decreases observed over " + CURE_TICK + " ticks at a 0.5 s interval "
                        + "(>=3 required — without this every other check here can pass vacuously)");

        boolean chipsSane = pulses > 0 && minChip > 0.0f && minChip >= 1.0f - EPS && maxChip <= 5.0f + EPS;
        check("inverted-range-safe", chipsSane,
                "chips in [" + ContamRig.fmt(pulses == 0 ? 0.0 : minChip) + ", " + ContamRig.fmt(maxChip)
                        + "] from a configured min=5.0 > max=1.0; expected every chip inside [1,5] and > 0");

        float drift = Math.abs(end - healthAtBaseline);
        check("cure-stops", cured && drift <= EPS,
                "health at t=" + POST_CURE_BASELINE_TICK + " was " + ContamRig.fmt(healthAtBaseline)
                        + ", at t=" + EVAL_TICK + " " + ContamRig.fmt(end) + " (drift " + ContamRig.fmt(drift) + ")");

        if (victim != null) {
            victim.discard();
        }
        ContamRig.release(ow, CX, CZ);
    }
}
