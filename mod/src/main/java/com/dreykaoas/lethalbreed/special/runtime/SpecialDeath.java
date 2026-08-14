package com.dreykaoas.lethalbreed.special.runtime;

import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.ExpertConfig;
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
        int count = SpecialVariantConfig.specialSplitterChildren;
        int spread = SpecialVariantConfig.specialSplitterSpread;
        // Floored like every other SCALE factor: the bound allows 0.05, and nothing should be able to shrink a
        // zombie to an invisible speck.
        double scale = Math.max(ExpertConfig.expertAttributeFloor,
                SpecialVariantConfig.specialSplitterChildScale);
        for (int i = 0; i < count; i++) {
            Zombie child = ChildSpawner.spawnNear(level, z, spread);
            if (child != null) {
                SpecialRoller.assign(child, SpecialType.NONE); // no chain-splitting
                // multiplyTotal, not multiplyBase: the child already carries its own rand_scale roll, and
                // summing the deltas made the real shrink drift between 0.53 and 0.68 instead of the flat 0.6
                // the option advertises. Composing gives exactly that fraction of whatever size it rolled.
                AttributeModifiers.multiplyTotal(child, Attributes.SCALE, "split_small", scale);
            }
        }
    }
}
