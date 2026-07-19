package com.dreykaoas.lethalbreed.effect;

import com.dreykaoas.lethalbreed.effect.contamination.ContaminationLifecycle;
import com.dreykaoas.lethalbreed.effect.contamination.ContaminationState;
import com.dreykaoas.lethalbreed.effect.contamination.ContaminationTick;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Drives the Super Contamination plague. Source of truth is a PERSISTENT integer attachment (the contamination
 * age, ticks) plus a PERSISTENT boolean flag (symptomatic yet?).
 *
 * <p>Two stages:
 * <ul>
 *   <li><b>Latent</b> (age &gt; 0, not symptomatic): nothing is shown — no HUD tint, no skull icon, no
 *       particles, no plague damage. The victim never knows. The only effect is a brief, particleless slow
 *       applied once at the moment of infection (a short movement-speed attribute modifier). Every 5–10
 *       in-game days a roll (2–10% chance) may surface the symptoms.</li>
 *   <li><b>Symptomatic</b> (age &gt; 0, symptomatic): the effect icon is re-applied every tick (so milk only
 *       hides the skull for one tick), the HUD hearts/food read green client-side, the victim takes the
 *       ramping plague pulse (health + player food), and three independent random episodes flare on their own
 *       timers: a movement slow, a no-jump lock, and a weak-strike (all particleless attribute modifiers).</li>
 * </ul>
 *
 * <p>The ONLY cure is staying crouched: each check has a tiny random chance (5–8%) to shake it. On death the
 * victim simply dies — the plague state is cleared; a humanoid may reanimate.
 *
 * <p>This class is the public facade, kept thin: it owns no state and no logic of its own. The actual attachments,
 * shared state, and behavior live under {@code com.dreykaoas.lethalbreed.effect.contamination} (state, episodes,
 * hallucination, lifecycle, symptoms, and the per-tick sweep) — this class only re-exports
 * {@link com.dreykaoas.lethalbreed.effect.contamination.ContaminationState}'s dev counters for the headless
 * harness and delegates every method straight through, so the dependency stays one-way (this facade
 * depends on {@code contamination}, never the reverse).
 */
public final class ContaminationManager {
    private ContaminationManager() {}

    // Dev instrumentation (headless harness).
    public static final java.util.concurrent.atomic.AtomicInteger INFECT_COUNT = ContaminationState.INFECT_COUNT;
    public static final java.util.concurrent.atomic.AtomicInteger DEATH_COUNT = ContaminationState.DEATH_COUNT;

    public static void init() {}

    /** Infect a victim (called from the zombie-hit hook). No-op if already contaminated or it's a zombie.
     *  Starts LATENT: nothing visible, no plague damage — only a brief particleless slow right now. */
    public static void contaminate(LivingEntity e) {
        ContaminationLifecycle.contaminate(e);
    }

    /** Re-track a contaminated entity after chunk reload (its attachment persists, the in-memory set doesn't).
     *  Only re-show the icon if it had already turned symptomatic. */
    public static void onLoad(Entity e) {
        ContaminationLifecycle.onLoad(e);
    }

    /** Death of a contaminated victim: clear the plague state, then reanimate as a zombie if it was a humanoid. */
    public static void onDeath(LivingEntity e, ServerLevel level) {
        ContaminationLifecycle.onDeath(e, level);
    }

    public static void tick(MinecraftServer server) {
        ContaminationTick.tick(server);
    }

    /** True once a victim carries the plague (contamination age > 0). Used to let immune mobs still accept
     *  effects once infected. */
    public static boolean isContaminated(LivingEntity e) {
        return ContaminationState.age(e) > 0;
    }

    /** True when the victim has already turned symptomatic (visible + damaging stage). */
    public static boolean isSymptomatic(LivingEntity e) {
        return ContaminationState.symptomatic(e);
    }

    /** Dev tool: immediately surface symptoms on a contaminated victim (skips the 5–10 in-game-day roll), so
     *  the visible/damaging stage can be inspected on demand. No-op if the victim isn't contaminated. */
    public static void forceSymptomatic(LivingEntity e) {
        ContaminationLifecycle.forceSymptomatic(e);
    }

    /** Dev tool: jump a victim straight to a plague level (infect + surface symptoms first if needed). Clamped
     *  to [1, maxLevel]. Rerolls the per-victim intensity for that level. */
    public static void forceLevel(LivingEntity e, int lvl) {
        ContaminationLifecycle.forceLevel(e, lvl);
    }

    /** Current plague level for external/dev readout (1 while symptomatic, 0 if not symptomatic). */
    public static int plagueLevel(LivingEntity e) {
        return ContaminationState.symptomatic(e) ? ContaminationState.level(e) : 0;
    }

    /** Dev tool: clear the plague from a victim outright (public wrapper over the internal cure). */
    public static void clearPlague(LivingEntity e) {
        ContaminationLifecycle.cure(e);
    }
}
