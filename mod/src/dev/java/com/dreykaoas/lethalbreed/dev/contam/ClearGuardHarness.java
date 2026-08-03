package com.dreykaoas.lethalbreed.dev.contam;

import com.dreykaoas.lethalbreed.dev.arena.ArenaBuilder;
import com.dreykaoas.lethalbreed.dev.harness.TickPhasedHarness;

import com.dreykaoas.lethalbreed.config.ConfigOverride;
import com.dreykaoas.lethalbreed.config.domain.DevTestConfig;
import com.dreykaoas.lethalbreed.dev.contam.ClearGuardRig;
import com.dreykaoas.lethalbreed.dev.contam.ContamRig;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import com.dreykaoas.lethalbreed.effect.contamination.ClearGuard;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.cow.Cow;

/**
 * Proves that milk and {@code /effect clear} — which are THE SAME METHOD — behave oppositely, and that the
 * {@link ClearGuard} thread-local that distinguishes them never leaks. The two cure routes and the victims
 * live in {@link ClearGuardRig}; this class owns the scenarios and the verdicts.
 *
 * <p>Set-up at t=20, read at t=40 so the plague's own per-tick sweep gets ~20 sweeps in between — a state
 * that only survives because nothing ticked is not a state that survives.
 */
public final class ClearGuardHarness extends TickPhasedHarness {

    public static final ClearGuardHarness INSTANCE = new ClearGuardHarness();

    private static final int CX = 210;
    /** First cow's Z; the rest march north-east from here, {@link #SPACING} apart. */
    private static final int CZ = ArenaBuilder.VERIFY_BAND_Z + 20;
    private static final int SPACING = 6;
    /** Arena is built (and force-loaded) about the MIDDLE of the cow row, so its 3x3 chunk block covers it. */
    private static final int CZ_MID = CZ + SPACING * 2;
    private static final int HALF_Z = SPACING * 2 + 4;
    private static final int COW_COUNT = 5;

    private static final int SETUP_TICK = 20;
    /** Fifth cow's clear runs LATER than the milk, on the same thread — that is the whole point of it. */
    private static final int LATE_CLEAR_TICK = 25;
    private static final int EVAL_TICK = 40;

    private Cow[] cows = new Cow[COW_COUNT];
    private boolean guardDisarmedAfterMilk;
    private boolean lateClearRan;

    private ClearGuardHarness() {
        super("clear", new Stage("cows", SETUP_TICK, EVAL_TICK));
    }

    @Override
    protected boolean enabled() {
        return DevTestConfig.devClearTest;
    }

    @Override
    protected void build(int stage, ServerLevel ow, MinecraftServer server, ConfigOverride cfg) {
        ContamRig.arena(ow, CX, CZ_MID, 4, HALF_Z);
        cows = ClearGuardRig.cowRow(ow, CX, CZ, SPACING, COW_COUNT);

        // 1 — milk on a symptomatic victim: the disease must survive the drink, icon included.
        ClearGuardRig.infect(cows[0], true);
        ClearGuardRig.milk(ow, cows[0]);
        // Read the guard on the server thread the instant apply() returned. A guard still armed here would
        // make the NEXT /effect clear on this thread silently refuse to cure.
        guardDisarmedAfterMilk = !ClearGuard.isMilk();

        // 2 — /effect clear on a LATENT victim, carrying no other effect at all. removeAllEffects() then
        // returns false, which is precisely the return the old @At("TAIL") handler was never bound to.
        ClearGuardRig.infect(cows[1], false);
        ClearGuardRig.commandClear(cows[1]);

        // 3 — /effect clear on a symptomatic victim.
        ClearGuardRig.infect(cows[2], true);
        ClearGuardRig.commandClear(cows[2]);

        // 4 — milk must still do its ordinary job: ordinary effects go, the plague stays.
        cows[3].addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, false, false, true));
        ClearGuardRig.infect(cows[3], true);
        ClearGuardRig.milk(ow, cows[3]);

        // 5 — armed for LATE_CLEAR_TICK; see observe().
        ClearGuardRig.infect(cows[4], false);
    }

    /**
     * Cow #5's cure, deliberately deferred to a later tick on the same (server) thread as the milk above. If
     * {@link ClearGuard} ever escaped its {@code finally}, this thread would still be marked "milk" and this
     * perfectly ordinary {@code /effect clear} would refuse to cure — a leak no same-tick check can see.
     */
    @Override
    protected void observe(int stage, ServerLevel ow, int tick) {
        if (tick == LATE_CLEAR_TICK) {
            lateClearRan = ClearGuardRig.commandClear(cows[4]);
        }
    }

    @Override
    protected void evaluate(int stage, ServerLevel ow, MinecraftServer server) {
        Cow milked = cows[0];
        boolean iconBack = milked != null && milked.getEffect(LethalBreedEffects.SUPER_CONTAMINATION) != null;
        boolean stillIll = milked != null && ContaminationManager.isContaminated(milked)
                && ContaminationManager.isSymptomatic(milked) && iconBack
                && ContaminationManager.plagueLevel(milked) > 0;
        check("milk-keeps-plague", stillIll,
                "contaminated=" + (milked != null && ContaminationManager.isContaminated(milked))
                        + " symptomatic=" + (milked != null && ContaminationManager.isSymptomatic(milked))
                        + " icon=" + iconBack
                        + " level=" + (milked == null ? -1 : ContaminationManager.plagueLevel(milked)));

        check("command-cures-latent",
                cows[1] != null && !ContaminationManager.isContaminated(cows[1]),
                "latent victim, no other effect: contaminated=" + (cows[1] != null && ContaminationManager.isContaminated(cows[1])));

        boolean symIconGone = cows[2] != null && cows[2].getEffect(LethalBreedEffects.SUPER_CONTAMINATION) == null;
        check("command-cures-symptomatic",
                cows[2] != null && !ContaminationManager.isContaminated(cows[2]) && symIconGone,
                "contaminated=" + (cows[2] != null && ContaminationManager.isContaminated(cows[2]))
                        + " iconGone=" + symIconGone);

        Cow c4 = cows[3];
        boolean speedGone = c4 != null && !c4.hasEffect(MobEffects.SPEED);
        boolean c4Icon = c4 != null && c4.getEffect(LethalBreedEffects.SUPER_CONTAMINATION) != null;
        check("milk-still-clears-effects",
                speedGone && c4 != null && ContaminationManager.isSymptomatic(c4) && c4Icon,
                "speedGone=" + speedGone + " symptomatic=" + (c4 != null && ContaminationManager.isSymptomatic(c4))
                        + " icon=" + c4Icon);

        check("guard-disarmed", guardDisarmedAfterMilk,
                "ClearGuard.isMilk() immediately after apply() returned = " + !guardDisarmedAfterMilk);

        check("no-guard-leak",
                lateClearRan && cows[4] != null && !ContaminationManager.isContaminated(cows[4]),
                "clear at t=" + LATE_CLEAR_TICK + " ran=" + lateClearRan + " contaminated="
                        + (cows[4] != null && ContaminationManager.isContaminated(cows[4])));

        ClearGuardRig.discard(cows);
        ContamRig.release(ow, CX, CZ_MID);
    }
}
