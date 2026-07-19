package com.dreykaoas.lethalbreed.entity.mood;

import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;

import net.minecraft.world.entity.monster.zombie.Zombie;

/** Self-heal while fleeing/sheltering/celebrating and still hurt (shared regen timer logic for ZombieMood). */
public final class MoodRegen {
    private MoodRegen() {}

    /** Applies a heal tick if eligible and due. Returns the (possibly updated) {@code lastRegenTime}: reset to
     *  {@code now} both right after a heal and whenever regen isn't eligible (so the next eligible spell waits a
     *  full interval before its first heal). */
    public static long tick(Zombie entity, boolean regenEligible, long now, long lastRegenTime) {
        if (!regenEligible) {
            return now;
        }
        if (now - lastRegenTime >= ZombieMoodConfig.regenIntervalTicks) {
            entity.heal((float) ZombieMoodConfig.regenAmount);
            return now;
        }
        return lastRegenTime;
    }
}
