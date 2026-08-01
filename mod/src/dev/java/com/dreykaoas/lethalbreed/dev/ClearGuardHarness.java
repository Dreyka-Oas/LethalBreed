package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.dev.contam.ContamRig;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import com.dreykaoas.lethalbreed.effect.contamination.ClearGuard;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;

/**
 * Proves that milk and {@code /effect clear} — which are THE SAME METHOD — behave oppositely, and that the
 * {@link ClearGuard} thread-local that distinguishes them never leaks.
 *
 * <p><b>Why these two calls are the real thing, not a mock.</b> Vanilla's
 * {@link ClearAllStatusEffectsConsumeEffect#apply} is literally {@code return livingEntity.removeAllEffects();}
 * — verified against this project's 1.21.11 mappings: the class is a record, {@code INSTANCE} is
 * {@code public static final}, and {@code apply(Level, ItemStack, LivingEntity)} is public. So:
 * <ul>
 *   <li>MILK path = {@code ClearAllStatusEffectsConsumeEffect.INSTANCE.apply(level, MILK_BUCKET, cow)} — the
 *       byte-for-byte call a player drinking milk makes, and the only method {@code MilkKeepsPlagueMixin}
 *       redirects inside.</li>
 *   <li>COMMAND path = {@code cow.removeAllEffects()} — exactly what {@code EffectCommands.clearEffects}
 *       calls.</li>
 * </ul>
 * Nothing here simulates the distinction; both routes bottom out in the shipped mixins.
 *
 * <p><b>Why not {@code /lethaldev}.</b> {@code LethalDevCommand.target()} calls
 * {@code getSource().getPlayerOrException()}, which throws for the console source a headless run has. Every
 * harness therefore drives {@link ContaminationManager} directly.
 *
 * <p>Set-up at t=20, read at t=40 so the plague's own per-tick sweep gets ~20 sweeps in between — a state that
 * only survives because nothing ticked is not a state that survives.
 */
public final class ClearGuardHarness {
    private ClearGuardHarness() {}

    private static final String SUITE = "clear";

    private static final int CX = 210;
    /** First cow's Z; the rest march north-east from here, {@link #SPACING} apart. */
    private static final int CZ = ArenaBuilder.VERIFY_BAND_Z + 20;
    private static final int SPACING = 6;
    /** Arena is built (and force-loaded) about the MIDDLE of the cow row, so its 3x3 chunk block covers it. */
    private static final int CZ_MID = CZ + SPACING * 2;
    private static final int HALF_Z = SPACING * 2 + 4;

    private static final int SETUP_TICK = 20;
    /** Fifth cow's clear runs LATER than the milk, on the same thread — that is the whole point of it. */
    private static final int LATE_CLEAR_TICK = 25;
    private static final int EVAL_TICK = 40;

    private static int tick = -1;
    private static final Cow[] COWS = new Cow[5];
    private static boolean guardDisarmedAfterMilk;
    private static boolean lateClearRan;

    public static void onTick(MinecraftServer server) {
        if (!ProgressionConfig.devClearTest || !FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        tick++;
        ServerLevel ow = server.overworld();
        if (tick == SETUP_TICK) {
            setUp(ow);
        } else if (tick == LATE_CLEAR_TICK) {
            lateClear();
        } else if (tick == EVAL_TICK) {
            evaluate(ow, server);
        }
    }

    private static void setUp(ServerLevel ow) {
        ContamRig.arena(ow, CX, CZ_MID, 4, HALF_Z);
        for (int i = 0; i < COWS.length; i++) {
            COWS[i] = ContamRig.cow(ow, CX, CZ + i * SPACING, 0.0f);
        }

        // 1 — milk on a symptomatic victim: the disease must survive the drink, icon included.
        infect(COWS[0], true);
        milk(ow, COWS[0]);
        // Read the guard on the server thread the instant apply() returned. A guard still armed here would
        // make the NEXT /effect clear on this thread silently refuse to cure.
        guardDisarmedAfterMilk = !ClearGuard.isMilk();

        // 2 — /effect clear on a LATENT victim, carrying no other effect at all. removeAllEffects() then
        // returns false, which is precisely the return the old @At("TAIL") handler was never bound to.
        infect(COWS[1], false);
        COWS[1].removeAllEffects();

        // 3 — /effect clear on a symptomatic victim.
        infect(COWS[2], true);
        COWS[2].removeAllEffects();

        // 4 — milk must still do its ordinary job: ordinary effects go, the plague stays.
        COWS[3].addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, false, false, true));
        infect(COWS[3], true);
        milk(ow, COWS[3]);

        // 5 — armed for LATE_CLEAR_TICK; see lateClear().
        infect(COWS[4], false);
    }

    /**
     * Cow #5's cure, deliberately deferred to a later tick on the same (server) thread as the milk above. If
     * {@link ClearGuard} ever escaped its {@code finally}, this thread would still be marked "milk" and this
     * perfectly ordinary {@code /effect clear} would refuse to cure — a leak no same-tick check can see.
     */
    private static void lateClear() {
        if (COWS[4] != null) {
            COWS[4].removeAllEffects();
            lateClearRan = true;
        }
    }

    private static void evaluate(ServerLevel ow, MinecraftServer server) {
        Cow milked = COWS[0];
        boolean iconBack = milked != null && milked.getEffect(LethalBreedEffects.SUPER_CONTAMINATION) != null;
        boolean stillIll = milked != null && ContaminationManager.isContaminated(milked)
                && ContaminationManager.isSymptomatic(milked) && iconBack
                && ContaminationManager.plagueLevel(milked) > 0;
        DevVerdict.check(SUITE, "milk-keeps-plague", stillIll,
                "contaminated=" + (milked != null && ContaminationManager.isContaminated(milked))
                        + " symptomatic=" + (milked != null && ContaminationManager.isSymptomatic(milked))
                        + " icon=" + iconBack
                        + " level=" + (milked == null ? -1 : ContaminationManager.plagueLevel(milked)));

        DevVerdict.check(SUITE, "command-cures-latent",
                COWS[1] != null && !ContaminationManager.isContaminated(COWS[1]),
                "latent victim, no other effect: contaminated=" + (COWS[1] != null && ContaminationManager.isContaminated(COWS[1])));

        boolean symIconGone = COWS[2] != null && COWS[2].getEffect(LethalBreedEffects.SUPER_CONTAMINATION) == null;
        DevVerdict.check(SUITE, "command-cures-symptomatic",
                COWS[2] != null && !ContaminationManager.isContaminated(COWS[2]) && symIconGone,
                "contaminated=" + (COWS[2] != null && ContaminationManager.isContaminated(COWS[2]))
                        + " iconGone=" + symIconGone);

        Cow c4 = COWS[3];
        boolean speedGone = c4 != null && !c4.hasEffect(MobEffects.SPEED);
        boolean c4Icon = c4 != null && c4.getEffect(LethalBreedEffects.SUPER_CONTAMINATION) != null;
        DevVerdict.check(SUITE, "milk-still-clears-effects",
                speedGone && c4 != null && ContaminationManager.isSymptomatic(c4) && c4Icon,
                "speedGone=" + speedGone + " symptomatic=" + (c4 != null && ContaminationManager.isSymptomatic(c4))
                        + " icon=" + c4Icon);

        DevVerdict.check(SUITE, "guard-disarmed", guardDisarmedAfterMilk,
                "ClearGuard.isMilk() immediately after apply() returned = " + !guardDisarmedAfterMilk);

        DevVerdict.check(SUITE, "no-guard-leak",
                lateClearRan && COWS[4] != null && !ContaminationManager.isContaminated(COWS[4]),
                "clear at t=" + LATE_CLEAR_TICK + " ran=" + lateClearRan + " contaminated="
                        + (COWS[4] != null && ContaminationManager.isContaminated(COWS[4])));

        for (Cow c : COWS) {
            if (c != null) {
                c.discard();
            }
        }
        ContamRig.release(ow, CX, CZ_MID);
        DevVerdict.summary(SUITE, server);
    }

    private static void infect(Cow c, boolean symptomatic) {
        if (c == null) {
            return;
        }
        ContaminationManager.contaminate(c);
        if (symptomatic) {
            ContaminationManager.forceSymptomatic(c);
        }
    }

    private static void milk(ServerLevel ow, Cow c) {
        if (c != null) {
            ClearAllStatusEffectsConsumeEffect.INSTANCE.apply(ow, new ItemStack(Items.MILK_BUCKET), c);
        }
    }
}
