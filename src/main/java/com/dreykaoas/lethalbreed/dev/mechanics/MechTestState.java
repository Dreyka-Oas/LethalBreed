package com.dreykaoas.lethalbreed.dev.mechanics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;

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

    /** Latch the current fire state of the sun-burn props; called every tick of the test window. */
    public void latchFire() {
        if (husk != null && husk.getRemainingFireTicks() > 0) {
            huskWasOnFire = true;
        }
        if (sunZombie != null && sunZombie.getRemainingFireTicks() > 0) {
            sunZombieWasOnFire = true;
        }
    }
}
