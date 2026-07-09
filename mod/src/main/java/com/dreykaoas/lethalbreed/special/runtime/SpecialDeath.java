package com.dreykaoas.lethalbreed.special.runtime;

import com.dreykaoas.lethalbreed.special.SpecialAttachment;
import com.dreykaoas.lethalbreed.special.SpecialRoller;
import com.dreykaoas.lethalbreed.special.SpecialType;
import com.dreykaoas.lethalbreed.util.AttributeModifiers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** DEATH specials that fire when a special zombie dies. */
public final class SpecialDeath {
    private SpecialDeath() {}

    /** A Splitter spawns two small, non-special children on death. */
    public static void onDeath(Zombie z, ServerLevel level) {
        if (SpecialType.fromId(z.getAttached(SpecialAttachment.SPECIAL)) != SpecialType.SPLITTER) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            Zombie child = ChildSpawner.spawnNear(level, z, 1);
            if (child != null) {
                SpecialRoller.assign(child, SpecialType.NONE); // no chain-splitting
                AttributeModifiers.multiplyBase(child, Attributes.SCALE, "split_small", 0.6); // 0.6x size
            }
        }
    }
}
