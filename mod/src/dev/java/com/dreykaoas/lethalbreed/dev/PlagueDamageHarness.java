package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.dev.contam.ContamRig;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.cow.Cow;

/**
 * Runs the plague pulse under an INVERTED damage range — {@code contamDamageMin=5.0} above
 * {@code contamDamageMax=1.0} — and proves the victim still only ever loses health.
 *
 * <p><b>Why deliberately mis-configure it.</b> {@code ConfigBoundsTable} clamps each option independently and
 * never the relation between two, so an operator can legitimately put min above max with both values in
 * range. Before {@code ContaminationRoll.uniform} reordered the pair, {@code min + rnd*(max-min)} drew a
 * NEGATIVE number and {@code setHealth(health - dmg)} HEALED the victim (audit #12). A rig that only ever
 * tests a well-ordered range can never see that. This one runs the exact configuration that broke.
 *
 * <p><b>Why {@code pulses-fired} is mandatory.</b> "Health never went up" is trivially true of a victim
 * nothing ever happened to. Without a positive lower bound on the number of observed decreases, every other
 * check in this suite passes vacuously the moment the pulse stops firing at all — the single most likely way
 * for this rig to rot. It is a hard check, not a log line.
 *
 * <p>The victim's MAX_HEALTH is raised (see {@link ContamRig#cow}) purely so a 1–5 chip every half-second does
 * not kill a 10-HP cow four pulses in; it is never healed, so the monotone assertion stays meaningful.
 * {@code contamMaxLevel} is pinned to 1 for the run so the level-up roll cannot scale the chip past 5 and turn
 * a working evolution mechanic into a false FAIL on the damage draw. Every mutated option is restored at the
 * end of the window.
 */
public final class PlagueDamageHarness {
    private PlagueDamageHarness() {}

    private static final String SUITE = "plague";

    private static final int CX = 30;
    private static final int CZ = ArenaBuilder.VERIFY_BAND_Z;
    private static final float VICTIM_HEALTH = 1024.0f;
    private static final float EPS = 1.0e-3f;

    private static final int CURE_TICK = 300;
    private static final int POST_CURE_BASELINE_TICK = 310;
    private static final int EVAL_TICK = 400;

    private static int tick = -1;
    private static Cow victim;

    private static float startHealth;
    private static float maxHealth;
    private static float lastHealth;
    private static int pulses;
    private static float minChip = Float.MAX_VALUE;
    private static float maxChip;
    private static float healthAtBaseline;
    private static boolean cured;

    // Saved configuration, restored verbatim at EVAL_TICK.
    private static double savedDmgMin;
    private static double savedDmgMax;
    private static double savedIntMin;
    private static double savedIntMax;
    private static int savedMaxLevel;

    public static void onTick(MinecraftServer server) {
        if (!ProgressionConfig.devPlagueTest || !FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        if (server.getTickCount() < ContamRig.DAMAGE_START) {
            return;
        }
        tick++;
        ServerLevel ow = server.overworld();
        if (tick == 0) {
            setUp(ow);
        } else if (tick < EVAL_TICK) {
            observe();
            if (tick == CURE_TICK) {
                cure();
            } else if (tick == POST_CURE_BASELINE_TICK) {
                healthAtBaseline = victim == null ? 0.0f : victim.getHealth();
            }
        } else if (tick == EVAL_TICK) {
            observe();
            evaluate(ow);
        }
    }

    private static void setUp(ServerLevel ow) {
        savedDmgMin = ContaminationConfig.contamDamageMin;
        savedDmgMax = ContaminationConfig.contamDamageMax;
        savedIntMin = ContaminationConfig.contamIntervalMinSec;
        savedIntMax = ContaminationConfig.contamIntervalMaxSec;
        savedMaxLevel = ContaminationConfig.contamMaxLevel;

        ContaminationConfig.contaminationEnabled = true;
        ContaminationConfig.contamDamageMin = 5.0;  // INVERTED on purpose — min above max (audit #12).
        ContaminationConfig.contamDamageMax = 1.0;
        ContaminationConfig.contamIntervalMinSec = 0.5;
        ContaminationConfig.contamIntervalMaxSec = 0.5;
        ContaminationConfig.contamMaxLevel = 1;

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

    private static void observe() {
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

    private static void cure() {
        if (victim != null) {
            ContaminationManager.clearPlague(victim);
        }
        cured = true;
    }

    private static void evaluate(ServerLevel ow) {
        float end = victim == null ? 0.0f : victim.getHealth();
        DevVerdict.check(SUITE, "monotone-down", victim != null && maxHealth <= startHealth + EPS,
                "start=" + ContamRig.fmt(startHealth) + " maxEverSeen=" + ContamRig.fmt(maxHealth)
                        + " end=" + ContamRig.fmt(end) + " pulses=" + pulses);

        DevVerdict.check(SUITE, "pulses-fired", pulses >= 3,
                pulses + " health decreases observed over " + CURE_TICK + " ticks at a 0.5 s interval "
                        + "(>=3 required — without this every other check here can pass vacuously)");

        boolean chipsSane = pulses > 0 && minChip > 0.0f && minChip >= 1.0f - EPS && maxChip <= 5.0f + EPS;
        DevVerdict.check(SUITE, "inverted-range-safe", chipsSane,
                "chips in [" + ContamRig.fmt(pulses == 0 ? 0.0 : minChip) + ", " + ContamRig.fmt(maxChip)
                        + "] from a configured min=5.0 > max=1.0; expected every chip inside [1,5] and > 0");

        float drift = Math.abs(end - healthAtBaseline);
        DevVerdict.check(SUITE, "cure-stops", cured && drift <= EPS,
                "health at t=" + POST_CURE_BASELINE_TICK + " was " + ContamRig.fmt(healthAtBaseline)
                        + ", at t=" + EVAL_TICK + " " + ContamRig.fmt(end) + " (drift " + ContamRig.fmt(drift) + ")");

        if (victim != null) {
            victim.discard();
        }
        ContamRig.release(ow, CX, CZ);

        ContaminationConfig.contamDamageMin = savedDmgMin;
        ContaminationConfig.contamDamageMax = savedDmgMax;
        ContaminationConfig.contamIntervalMinSec = savedIntMin;
        ContaminationConfig.contamIntervalMaxSec = savedIntMax;
        ContaminationConfig.contamMaxLevel = savedMaxLevel;
    }
}
