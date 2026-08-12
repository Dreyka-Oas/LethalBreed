package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.dev.harness.TickPhasedHarness;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.ConfigOverride;
import com.dreykaoas.lethalbreed.dev.config.DevTestConfig;
import com.dreykaoas.lethalbreed.dev.mechanics.MechPhaseArena;
import com.dreykaoas.lethalbreed.dev.mechanics.MechSunArena;
import com.dreykaoas.lethalbreed.dev.mechanics.MechTestArena;
import com.dreykaoas.lethalbreed.dev.mechanics.MechTestState;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Headless verification of the non-special mechanics: daylight burn (including Husk), phase-scaled stats, the
 * Super Contamination plague, and the flee + distress-rally scenario.
 *
 * <p><b>This suite reported nothing to the verification system until now.</b> It logged its own
 * {@code [MechTest] name : PASS} lines and finished with {@code [MechTest] DONE} — neither of which is the
 * {@code [LB-Verify]} contract every other rig speaks, and neither of which is the {@code ALL DONE} marker the
 * gate requires. {@code LB_DEV_TEST=mech} was therefore listed as a valid suite that could never pass a gate,
 * and four checks ran every time with nobody counting them. It now goes through {@link DevVerdict} like the
 * rest, which is also what emits the summary and the marker.
 *
 * <p><b>Two stages, because one of them poisons the other.</b> The gear area needs phase 15 to get scaled
 * stats; sun-burn stops at {@code sunImmunePhase} (5) by design. Built in the same tick, as they were, the
 * phase bump silently disabled the burn before its window opened — so {@code sunburn} could not pass, ever.
 * The burn now runs first at a low phase and is judged before the phase climbs.
 *
 * <p>The phase is process-global and is not a config option, so {@link ConfigOverride} cannot restore it; it is
 * captured on the way in and put back after the last stage. Everything else this suite changes — and it used
 * to write ten {@code ContaminationConfig} fields with no restore at all — goes through the scope.
 */
public final class MechanicsTestHarness extends TickPhasedHarness {

    public static final MechanicsTestHarness INSTANCE = new MechanicsTestHarness();

    private static final int SUN_BUILD = 5;
    /** Generous: sun-burn only applies on a LOD bucket activation, and a freshly force-loaded headless arena
     *  needs a few ticks for skylight to settle before anything can ignite. */
    private static final int SUN_EVAL = 405;
    private static final int PHASE_BUILD = 420;
    private static final int PHASE_EVAL = 820;

    private final MechTestState state = new MechTestState();
    private int phaseBefore = -1;

    private MechanicsTestHarness() {
        super("mech", true,
                new Stage("sunburn", SUN_BUILD, SUN_EVAL),
                new Stage("phase15", PHASE_BUILD, PHASE_EVAL));
    }

    @Override
    protected boolean enabled() {
        return DevTestConfig.devMechTest;
    }

    @Override
    protected void build(int stage, ServerLevel ow, MinecraftServer server, ConfigOverride cfg) {
        if (stage == 0) {
            phaseBefore = PhaseManager.current();
            MechTestArena.worldRules(ow, server);
            PhaseManager.get().setPhase(server, 1); // below sunImmunePhase: the burn is live
            MechSunArena.build(ow, state, cfg);
        } else {
            MechPhaseArena.build(ow, server, state, cfg);
        }
    }

    @Override
    protected void observe(int stage, ServerLevel ow, int tick) {
        if (stage == 0) {
            // Latch every tick: a prop can ignite and burn to death inside the window, and the instantaneous
            // fire state at the evaluation tick would then read as "never burned".
            state.latchFire();
        } else {
            // Same reasoning for the rally: sound memory expires in about ten seconds, so a single
            // end-of-window sample would miss a rally that fired and lapsed.
            state.latchRally();
        }
    }

    @Override
    protected void evaluate(int stage, ServerLevel ow, MinecraftServer server) {
        if (stage == 0) {
            MechSunArena.evaluate(ow, state, this::check);
            return;
        }
        MechPhaseArena.evaluate(ow, state, this::check);
        if (phaseBefore >= 0) {
            PhaseManager.get().setPhase(server, phaseBefore);
            LethalBreed.LOGGER.info("[MechTest] phase restored to {}", phaseBefore);
        }
    }
}
