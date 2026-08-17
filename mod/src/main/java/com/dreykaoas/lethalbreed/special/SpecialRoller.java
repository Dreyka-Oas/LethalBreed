package com.dreykaoas.lethalbreed.special;

import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.ExpertConfig;

import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import com.dreykaoas.lethalbreed.util.AttributeModifiers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
        // finalizeSpawn chain — including its OWN special roll — and only THEN gets assign(NONE). Returning
        // early left those passives in place: a child re-labelled "none" kept Resistance II, double health and
        // a spc_scale of +0.40 that exactly cancels the -0.40 of split_small, so the "small child" came out
        // full size, twice as tough, and (with specialShowName) still wearing a "Juggernaut" nametag.
        SpecialType previous = SpecialType.fromId(z.getAttached(SpecialAttachment.SPECIAL));
        if (previous != type && previous != SpecialType.NONE) {
            clearPassive(z, previous);
        }
        z.setAttached(SpecialAttachment.SPECIAL, type.id());
        if (type == SpecialType.NONE) {
            return;
        }
        if (SpecialVariantConfig.specialShowName) {
            z.setCustomName(Component.translatable(type.translationKey()));
            z.setCustomNameVisible(true);
        }
        applyPassive(z, type);
    }

    private static SpecialType pickWeighted(List<SpecialType> pool, Random r) {
        int total = 0;
        for (SpecialType t : pool) {
            total += t.weight();
        }
        // Every unlocked type is weighted 0, so the player has switched them all off. The old code clamped the
        // bound to 1 and then fell through to pool.get(size-1), handing back a type whose weight explicitly
        // said "never" — most visible at phase 2, where the pool is SPRINTER alone and zeroing its weight
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

    /** Undo exactly what {@link #applyPassive} stamps for {@code type} — and nothing else, so a buff the
     *  zombie drew from the random effect pool survives being re-labelled. */
    private static void clearPassive(Zombie z, SpecialType type) {
        switch (type) {
            case SPRINTER -> {
                z.removeEffect(MobEffects.SPEED);
                AttributeModifiers.remove(z, Attributes.MOVEMENT_SPEED, "spc_speed");
            }
            case LEAPER -> z.removeEffect(LethalBreedEffects.LEAP);
            case JUGGERNAUT -> {
                AttributeModifiers.remove(z, Attributes.SCALE, "spc_scale");
                AttributeModifiers.remove(z, Attributes.MAX_HEALTH, "spc_hp");
                z.removeEffect(MobEffects.RESISTANCE);
                // applyPassive topped the pool up to the inflated maximum; shrink back into the new one.
                z.setHealth(Math.min(z.getHealth(), z.getMaxHealth()));
            }
            default -> { /* ACTIVE / DEATH stamp nothing at assign time */ }
        }
        if (z.hasCustomName()) {
            z.setCustomName(null);
            z.setCustomNameVisible(false);
        }
    }

    private static void applyPassive(Zombie z, SpecialType type) {
        switch (type) {
            case SPRINTER -> {
                infinite(z, MobEffects.SPEED, SpecialVariantConfig.specialSprinterSpeedAmp);
                mul(z, Attributes.MOVEMENT_SPEED, "spc_speed", SpecialVariantConfig.specialSprinterSpeedMul);
            }
            case LEAPER -> infinite(z, LethalBreedEffects.LEAP, SpecialVariantConfig.specialLeaperLeapAmp);
            case JUGGERNAUT -> {
                // Bulky tank via size/HP/resistance only — no armor (zombies never wear gear).
                // The scale-up is skipped where the ceiling is too low. This runs at the TAIL of
                // finalizeSpawn, i.e. AFTER vanilla accepted the spot using the UNSCALED silhouette, so
                // growing regardless pushed the zombie's head into the ceiling of any two-block mine gallery:
                // isInWall then deals IN_WALL damage every tick and the rarest variant quietly kills itself
                // underground. A Juggernaut that cannot grow keeps its health and resistance, which is a
                // better outcome than one that suffocates.
                if (hasHeadroom(z, SpecialVariantConfig.specialJuggernautScale)) {
                    mul(z, Attributes.SCALE, "spc_scale", SpecialVariantConfig.specialJuggernautScale);
                }
                mul(z, Attributes.MAX_HEALTH, "spc_hp", SpecialVariantConfig.specialJuggernautHealthMul);
                z.setHealth(z.getMaxHealth());
                infinite(z, MobEffects.RESISTANCE, SpecialVariantConfig.specialJuggernautResistanceAmp);
            }
            default -> { /* ACTIVE / DEATH: handled at runtime */ }
        }
    }

    /** Whether the column above {@code z} can hold it once grown by {@code scale}. */
    private static boolean hasHeadroom(Zombie z, double scale) {
        if (scale <= 1.0) {
            return true;
        }
        int needed = Mth.ceil(z.getBbHeight() * scale);
        BlockPos foot = z.blockPosition();
        for (int dy = 0; dy < needed; dy++) {
            BlockPos at = foot.above(dy);
            if (!z.level().getBlockState(at).getCollisionShape(z.level(), at).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void infinite(Zombie z, Holder<MobEffect> effect, int amp) {
        LethalBreedEffects.applyInfinite(z, effect, amp);
    }

    private static void mul(Zombie z, Holder<Attribute> attr, String idPath, double factor) {
        // Same floor ZombieVariation.applyMultiplier imposes on these two attributes, and for the same reason.
        // The bounds allow 0, and the deltas of every ADD_MULTIPLIED_BASE modifier on an attribute SUM: a
        // specialSprinterSpeedMul of 0 contributes -1.0, which drags the total negative and clamps the value
        // to zero. The Sprinter then spawns completely immobile, wearing Speed II and its own nametag.
        if (attr == Attributes.SCALE || attr == Attributes.MOVEMENT_SPEED) {
            factor = Math.max(ExpertConfig.expertAttributeFloor, factor);
        }
        AttributeModifiers.multiplyBase(z, attr, idPath, factor);
    }
}
