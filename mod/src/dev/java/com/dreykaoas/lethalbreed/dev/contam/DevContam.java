package com.dreykaoas.lethalbreed.dev.contam;

import com.dreykaoas.lethalbreed.effect.contamination.ContaminationLifecycle;
import com.dreykaoas.lethalbreed.effect.contamination.ContaminationState;

import net.minecraft.world.entity.LivingEntity;

/**
 * The plague-forcing dev tools that used to sit in {@code ContaminationManager} /
 * {@link ContaminationLifecycle} (src/main). They live here, in the dev source set, because only
 * {@link com.dreykaoas.lethalbreed.dev.command.LethalDevCommand} and the plague dev harnesses ever need to
 * force a victim's stage on demand.
 *
 * <p>{@code forceLevel} used to live here too. It moved back to {@link ContaminationLifecycle} when
 * {@code /lethaldev level} became a shipped command — the jar needs the capability, so hiding it in the
 * dev source set would only have meant a second copy.
 */
public final class DevContam {
    private DevContam() {}

    /** Dev tool: immediately surface symptoms on a contaminated victim (skips the 5–10 in-game-day roll), so the
     *  visible/damaging stage can be inspected on demand. No-op if the victim isn't contaminated. */
    public static void forceSymptomatic(LivingEntity e) {
        if (ContaminationState.age(e) <= 0 || ContaminationState.symptomatic(e)) {
            return;
        }
        e.setAttached(ContaminationState.SYMPTOMATIC, true);
        ContaminationState.setLevel(e, 1);
        ContaminationState.NEXT_SYMPTOM_ROLL_TICK.remove(e);
    }

}
