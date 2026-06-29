package com.dreykaoas.lethalbreed.special.runtime;

import com.dreykaoas.lethalbreed.special.SpecialAttachment;
import com.dreykaoas.lethalbreed.special.SpecialRoller;
import com.dreykaoas.lethalbreed.special.SpecialType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
            int dx = level.getRandom().nextInt(3) - 1;
            int dz = level.getRandom().nextInt(3) - 1;
            Zombie child = EntityType.ZOMBIE.spawn(level, z.blockPosition().offset(dx, 0, dz),
                    EntitySpawnReason.MOB_SUMMONED);
            if (child != null) {
                SpecialRoller.assign(child, SpecialType.NONE); // no chain-splitting
                AttributeInstance sc = child.getAttribute(Attributes.SCALE);
                if (sc != null) {
                    sc.addOrReplacePermanentModifier(new AttributeModifier(
                            Identifier.fromNamespaceAndPath("lethalbreed", "split_small"),
                            -0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)); // 0.6x size
                }
            }
        }
    }
}
