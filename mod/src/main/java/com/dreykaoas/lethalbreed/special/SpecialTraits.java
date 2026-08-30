package com.dreykaoas.lethalbreed.special;

import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.ExpertConfig;
import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import com.dreykaoas.lethalbreed.util.AttributeModifiers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The permanent traits a PASSIVE variant carries: the attribute multipliers and the infinite effects that
 * make a Juggernaut a Juggernaut whether or not anything is happening.
 *
 * <p>Separated from {@link SpecialRoller}, which decides WHICH variant a zombie draws. These two change for
 * different reasons: the draw follows the phase table, the traits follow the balance numbers. They also
 * come in pairs, apply and clear, and keeping the pair side by side is what keeps a re-roll from leaving a
 * modifier behind.
 */
final class SpecialTraits {
    private SpecialTraits() {}

    /** Undo exactly what {@link #applyPassive} stamps for {@code type}, and nothing else, so a buff the
     *  zombie drew from the random effect pool survives being re-labelled. */
    static void clear(Zombie z, SpecialType type) {
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

    static void apply(Zombie z, SpecialType type) {
        switch (type) {
            case SPRINTER -> {
                infinite(z, MobEffects.SPEED, SpecialVariantConfig.specialSprinterSpeedAmp);
                mul(z, Attributes.MOVEMENT_SPEED, "spc_speed", SpecialVariantConfig.specialSprinterSpeedMul);
            }
            case LEAPER -> infinite(z, LethalBreedEffects.LEAP, SpecialVariantConfig.specialLeaperLeapAmp);
            case JUGGERNAUT -> {
                // Bulky tank via size/HP/resistance only: no armor (zombies never wear gear).
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
    }}
