package com.dreykaoas.lethalbreed.effect;

import com.dreykaoas.lethalbreed.LethalBreed;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Brewable "Super Contamination" potion. Awkward Potion + Rotten Flesh → Contamination Potion. Drinking (or
 * splashing) it applies the Super Contamination effect; {@link SuperContaminationEffect#onEffectStarted}
 * bootstraps the plague from there, so a thrown splash/lingering potion infects anything it hits.
 * Splash + lingering variants are derived automatically by vanilla from the base potion.
 */
public final class LethalBreedPotions {
    private LethalBreedPotions() {}

    public static Holder<Potion> SUPER_CONTAMINATION;

    public static void register() {
        SUPER_CONTAMINATION = Registry.registerForHolder(BuiltInRegistries.POTION,
                Identifier.fromNamespaceAndPath(LethalBreed.MOD_ID, "super_contamination"),
                new Potion("super_contamination",
                        new MobEffectInstance(LethalBreedEffects.SUPER_CONTAMINATION, 600, 0)));

        FabricBrewingRecipeRegistryBuilder.BUILD.register(builder ->
                builder.registerPotionRecipe(Potions.AWKWARD,
                        Ingredient.of(Items.ROTTEN_FLESH), SUPER_CONTAMINATION));
    }
}
