package oas.dreyka.lethalbreed.special;

import oas.dreyka.lethalbreed.config.domain.SpecialVariantConfig;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.List;
import java.util.Random;

/**
 * Rolls a {@link SpecialType} for a zombie at spawn (chance scaling with the phase, harder types unlocked at
 * higher phases), stores it on the persistent attachment, sets the floating name, and applies PASSIVE buffs
 * immediately (so they're present from spawn and persist in NBT). ACTIVE/DEATH types do their work later
 * ({@link SpecialBehavior}).
 */
public final class SpecialRoller {
    private SpecialRoller() {}

    public static void roll(Zombie z, Random r, int phase) {
        if (!SpecialVariantConfig.specialEnabled) {
            return;
        }
        double chance = Math.min(SpecialVariantConfig.specialMaxChance,
                SpecialVariantConfig.specialBaseChance + phase * SpecialVariantConfig.specialPhaseScale);
        if (r.nextDouble() >= chance) {
            return;
        }
        List<SpecialType> pool = SpecialType.available(phase);
        if (pool.isEmpty()) {
            return;
        }
        SpecialType type = pickWeighted(pool, r);
        assign(z, type);
    }

    /** Force a specific type (used by the test command and Splitter children = NONE). */
    public static void assign(Zombie z, SpecialType type) {
        // Strip whatever the previous type had already stamped. A Splitter child is spawned, runs the whole
        // finalizeSpawn chain (including its OWN special roll) and only THEN gets assign(NONE). Returning
        // early left those passives in place: a child re-labelled "none" kept Resistance II, double health and
        // a spc_scale of +0.40 that exactly cancels the -0.40 of split_small, so the "small child" came out
        // full size, twice as tough, and (with specialShowName) still wearing a "Juggernaut" nametag.
        SpecialType previous = SpecialType.fromId(z.getAttached(SpecialAttachment.SPECIAL));
        if (previous != type && previous != SpecialType.NONE) {
            SpecialTraits.clear(z, previous);
        }
        z.setAttached(SpecialAttachment.SPECIAL, type.id());
        if (type == SpecialType.NONE) {
            return;
        }
        if (SpecialVariantConfig.specialShowName) {
            z.setCustomName(Component.translatable(type.translationKey()));
            z.setCustomNameVisible(true);
        }
        SpecialTraits.apply(z, type);
    }

    private static SpecialType pickWeighted(List<SpecialType> pool, Random r) {
        int total = 0;
        for (SpecialType t : pool) {
            total += t.weight();
        }
        // Every unlocked type is weighted 0, so the player has switched them all off. The old code clamped the
        // bound to 1 and then fell through to pool.get(size-1), handing back a type whose weight explicitly
        // said "never", most visible at phase 2, where the pool is SPRINTER alone and zeroing its weight
        // produced 100 % Sprinters.
        if (total <= 0) {
            return SpecialType.NONE;
        }
        int pick = r.nextInt(total);
        for (SpecialType t : pool) {
            pick -= t.weight();
            if (pick < 0) {
                return t;
            }
        }
        return pool.get(pool.size() - 1);
    }
}
