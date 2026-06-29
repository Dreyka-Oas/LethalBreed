package com.dreykaoas.lethalbreed.effect;

import com.dreykaoas.lethalbreed.LethalBreed;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Registers LethalBreed's custom mob effects into the vanilla {@code MOB_EFFECT} registry. Must be called
 * during mod init (registries are still open). {@code registerForHolder} returns a {@link Holder} so the
 * effect can be read/applied exactly like a vanilla one (e.g. {@code MobEffects.JUMP_BOOST} is also a Holder).
 */
public final class LethalBreedEffects {
    private LethalBreedEffects() {}

    /** Custom "Leap" effect — boosts a zombie's horizontal leap reach (read in SmartZombie.leapDistanceFactor). */
    public static Holder<MobEffect> LEAP;

    /** "Super Contamination" — lethal ramping plague that zombifies its victim (skull icon). */
    public static Holder<MobEffect> SUPER_CONTAMINATION;

    public static void register() {
        LEAP = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(LethalBreed.MOD_ID, "leap"),
                new LeapEffect(MobEffectCategory.BENEFICIAL, 0x66FF66));
        SUPER_CONTAMINATION = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(LethalBreed.MOD_ID, "super_contamination"),
                new SuperContaminationEffect(MobEffectCategory.HARMFUL, 0x3A5F0B));
    }
}
