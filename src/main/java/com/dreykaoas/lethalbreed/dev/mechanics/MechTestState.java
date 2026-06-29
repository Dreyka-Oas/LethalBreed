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
}
