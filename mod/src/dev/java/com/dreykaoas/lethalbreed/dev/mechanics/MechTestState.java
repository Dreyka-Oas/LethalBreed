package com.dreykaoas.lethalbreed.dev.mechanics;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.List;

/** Mutable handles to the mechanics-arena props, set during setup and read during evaluation. */
public final class MechTestState {
    static final int Y = 101;

    Husk husk;
    Zombie sunZombie;
    BlockPos gearPos;
    BlockPos contamPos;
    // Latched true if the prop caught fire at ANY point in the window. Sun-burn can ignite then KILL the
    // mob before the final evaluation tick, so checking only the instantaneous fire state is flaky.
    boolean huskWasOnFire;
    boolean sunZombieWasOnFire;

    // ---- Flee / distress-rally scenario ----
    Zombie fleer;
    /** What the fleer runs from. Held so the window can keep the aggressor memory alive — see
     *  {@link #refreshFleeThreat}. */
    LivingEntity fleeThreat;
    List<Zombie> rallyHelpers = List.of();
    /** Latched true if any idle helper acquired sound-memory (rallied to the fleer) during the window. */
    boolean rallyHelped;

    /** Latch the current fire state of the sun-burn props; called every tick of the test window. */
    public void latchFire() {
        if (husk != null && husk.getRemainingFireTicks() > 0) {
            huskWasOnFire = true;
        }
        if (sunZombie != null && sunZombie.getRemainingFireTicks() > 0) {
            sunZombieWasOnFire = true;
        }
    }

    /**
     * Re-arm the fleer's aggressor memory, every tick of the window.
     *
     * <p>Vanilla clears {@code lastHurtByMob} about 100 ticks after it is set, and the rally window is 400.
     * Once it lapses, {@code MoodStateDispatch.currentThreat} returns null and {@code flightThreat} falls
     * back to the nearest PLAYER — of which a headless server has none. The zombie is then fleeing with a
     * null threat, and the distress scream, which requires a non-null one, can never fire again.
     *
     * <p>That left the scream possible only inside the first ~100 ticks, and only on a mood tick where the
     * state had already latched to FLEEING — a race the check lost more often than it won, reporting zero
     * screams while helpers rallied off some other sound. Refreshing the memory makes the scenario the one
     * the check describes: a wounded zombie with a LIVE threat 15 blocks away.
     */
    public void refreshFleeThreat() {
        if (fleer != null && !fleer.isRemoved() && fleeThreat != null && fleeThreat.isAlive()) {
            fleer.setLastHurtByMob(fleeThreat);
        }
    }

    /** Latch whether any idle helper has picked up sound-memory this tick. Memory expires (~10 s), so a
     *  single end-of-window sample would miss it — sample every tick like {@link #latchFire}. */
    public void latchRally() {
        if (rallyHelped) {
            return;
        }
        for (Zombie h : rallyHelpers) {
            SmartZombie sz = GameState.REGISTRY.get(h.getId());
            if (sz != null && sz.pursuit().hasMemory()) {
                rallyHelped = true;
                return;
            }
        }
    }
}
