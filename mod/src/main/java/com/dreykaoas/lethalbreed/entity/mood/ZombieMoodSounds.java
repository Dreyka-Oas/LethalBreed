package com.dreykaoas.lethalbreed.entity.mood;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** Sound helper for {@code ZombieMood}: the amplified vanilla zombie groan used for both the victory cheer and
 *  the distress scream. */
public final class ZombieMoodSounds {
    private ZombieMoodSounds() {}

    /** Play the amplified vanilla zombie groan at {@code entity}. Volume &gt;1 widens the audible range. */
    public static void scream(Zombie entity, ServerLevel level, float volume, float pitch) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.ZOMBIE_AMBIENT, entity.getSoundSource(), volume, pitch);
    }
}
