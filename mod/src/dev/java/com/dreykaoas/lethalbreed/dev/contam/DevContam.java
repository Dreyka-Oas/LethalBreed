package com.dreykaoas.lethalbreed.dev.contam;

import com.dreykaoas.lethalbreed.effect.contamination.ContaminationLifecycle;
import com.dreykaoas.lethalbreed.effect.contamination.ContaminationState;

import net.minecraft.world.entity.LivingEntity;

/**
 * The plague-forcing dev tools that used to sit in {@code ContaminationManager} /
 * {@link ContaminationLifecycle} (src/main). They live here, in the dev source set, so the shipped jar has no
 * plague-mutation entry point — only {@link com.dreykaoas.lethalbreed.dev.command.LethalDevCommand} and the
 * plague dev harnesses ever need to force a victim's stage/level on demand.
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
        ContaminationState.nextSymptomRoll.remove(e);
    }

    /** Dev tool: jump a victim straight to a plague level (infect + surface symptoms first if needed). Clamped
     *  to [1, maxLevel]. Rerolls the per-victim intensity for that level. */
    public static void forceLevel(LivingEntity e, int lvl) {
        if (ContaminationState.age(e) <= 0) {
            ContaminationLifecycle.contaminate(e);
        }
        if (ContaminationState.age(e) <= 0) {
            return; // contamination disabled or entity ineligible
        }
        if (!ContaminationState.symptomatic(e)) {
            e.setAttached(ContaminationState.SYMPTOMATIC, true);
            ContaminationState.nextSymptomRoll.remove(e);
        }
        ContaminationState.setLevel(e, lvl);
    }
}
