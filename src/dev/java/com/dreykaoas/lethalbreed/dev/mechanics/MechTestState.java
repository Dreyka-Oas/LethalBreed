package com.dreykaoas.lethalbreed.dev.mechanics;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import net.minecraft.core.BlockPos;
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
