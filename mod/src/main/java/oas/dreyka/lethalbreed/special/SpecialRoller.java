package oas.dreyka.lethalbreed.special;

import oas.dreyka.lethalbreed.api.variant.SpecialVariant;
import oas.dreyka.lethalbreed.api.variant.SpecialVariantRegistry;
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
        List<SpecialVariant> pool = SpecialVariantRegistry.available(phase);
        if (pool.isEmpty()) {
            return;
        }
        assign(z, pickWeighted(pool, r));
    }

    /** Force one of the shipped types. The road the test command and the Splitter's children take. */
    public static void assign(Zombie z, SpecialType type) {
        assign(z, type == SpecialType.NONE ? null : SpecialVariantRegistry.byId(type.id()));
    }

    /** Force any variant, this mod's or a stranger's; null strips whatever the zombie was carrying. */
    public static void assign(Zombie z, SpecialVariant variant) {
        // Strip whatever the previous variant had already stamped. A Splitter child is spawned, runs the whole
        // finalizeSpawn chain (including its OWN special roll) and only THEN gets assign(null). Returning
        // early left those passives in place: a child re-labelled "none" kept Resistance II, double health and
        // a spc_scale of +0.40 that exactly cancels the -0.40 of split_small, so the "small child" came out
        // full size, twice as tough, and (with specialShowName) still wearing a "Juggernaut" nametag.
        SpecialVariant previous = SpecialVariantRegistry.byId(z.getAttached(SpecialAttachment.SPECIAL));
        if (previous != null && previous != variant) {
            previous.behavior().onUnassign(z);
        }
        z.setAttached(SpecialAttachment.SPECIAL, variant == null ? SpecialType.NONE.id() : variant.id());
        if (variant == null) {
            return;
        }
        if (SpecialVariantConfig.specialShowName) {
            z.setCustomName(Component.translatable(variant.translationKey()));
            z.setCustomNameVisible(true);
        }
        variant.behavior().onSpawn(z);
    }

    private static SpecialVariant pickWeighted(List<SpecialVariant> pool, Random r) {
        int total = 0;
        for (SpecialVariant v : pool) {
            total += Math.max(0, v.weight().getAsInt());
        }
        // Every unlocked variant is weighted 0, so the player has switched them all off. The old code clamped
        // the bound to 1 and then fell through to pool.get(size-1), handing back a type whose weight explicitly
        // said "never", most visible at phase 2, where the pool is SPRINTER alone and zeroing its weight
        // produced 100 % Sprinters.
        if (total <= 0) {
            return null;
        }
        int pick = r.nextInt(total);
        for (SpecialVariant v : pool) {
            pick -= Math.max(0, v.weight().getAsInt());
            if (pick < 0) {
                return v;
            }
        }
        return pool.get(pool.size() - 1);
    }
}
