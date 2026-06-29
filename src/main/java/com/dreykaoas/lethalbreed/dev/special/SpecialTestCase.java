package com.dreykaoas.lethalbreed.dev.special;

import com.dreykaoas.lethalbreed.special.SpecialType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** One special-variant verification case: the forced special zombie plus its props. */
public record SpecialTestCase(SpecialType type, Zombie z, Cow cow, Zombie extra, BlockPos pos) {
    static final int Y = 101;
    static final int SPACING = 60; // > detectRadius (10) so cases don't cross-target
}
