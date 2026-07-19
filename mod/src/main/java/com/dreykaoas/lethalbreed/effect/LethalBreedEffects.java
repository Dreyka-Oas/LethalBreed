package com.dreykaoas.lethalbreed.effect;

import com.dreykaoas.lethalbreed.LethalBreed;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

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

    /** "Zombie Vision" — transient hallucination episode; while present the victim's client draws other players as
     *  zombies. Flares on its own random timer like the other symptomatic episodes. No icon, purely a client cue. */
    public static Holder<MobEffect> ZOMBIE_VISION;

    /** Whether the effect is registered and currently on {@code entity} — the shared null-guarded membership
     *  check behind the contamination HUD tint and the hallucination particle cue. */
    public static boolean isSuperContaminated(LivingEntity entity) {
        return SUPER_CONTAMINATION != null && entity.hasEffect(SUPER_CONTAMINATION);
    }

    /** Apply an INFINITE-duration effect at {@code amplifier}, hidden (no ambient, no particles) but with its
     *  icon — the shared "buff for the zombie's whole life, invisible to players" idiom used by the special
     *  roller and the per-zombie variation roll. */
    public static void applyInfinite(LivingEntity entity, Holder<MobEffect> effect, int amplifier) {
        entity.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier,
                false, false, true));
    }

    public static void register() {
        LEAP = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(LethalBreed.MOD_ID, "leap"),
                new LeapEffect(MobEffectCategory.BENEFICIAL, 0x66FF66));
        SUPER_CONTAMINATION = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(LethalBreed.MOD_ID, "super_contamination"),
                new SuperContaminationEffect(MobEffectCategory.HARMFUL, 0x3A5F0B));
        ZOMBIE_VISION = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(LethalBreed.MOD_ID, "zombie_vision"),
                new SuperContaminationEffect(MobEffectCategory.HARMFUL, 0x3A5F0B));
    }
}
