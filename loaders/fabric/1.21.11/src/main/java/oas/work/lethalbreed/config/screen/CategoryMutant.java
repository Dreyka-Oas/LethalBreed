/**
 * Project: Lethal Breed
 * Responsibility: Config Category - Mutant
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.config.screen;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import oas.work.lethalbreed.config.ModConfig;
import net.minecraft.text.Text;

public class CategoryMutant {
    public static ConfigCategory build(ModConfig config) {
        return ConfigCategory.createBuilder()
            .name(Text.translatable("lethalbreed.config.category.mutant"))
            .option(Option.<Float>createBuilder().name(Text.translatable("lethalbreed.config.option.mutant_chance")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.mutant_chance.desc"))).binding(0.05f, () -> config.mutant.mutantChance, v -> config.mutant.mutantChance = v).controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 1.0f).step(0.01f)).build())
            .option(Option.<Integer>createBuilder().name(Text.translatable("lethalbreed.config.option.minion_count")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.minion_count.desc"))).binding(8, () -> config.mutant.mutantMinionCount, v -> config.mutant.mutantMinionCount = v).controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 32).step(1)).build())
            .option(Option.<Integer>createBuilder().name(Text.translatable("lethalbreed.config.option.mutant_tick")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.mutant_tick.desc"))).binding(5, () -> config.mutant.mutantTentacleTickRate, v -> config.mutant.mutantTentacleTickRate = v).controller(opt -> IntegerSliderControllerBuilder.create(opt).range(1, 100).step(1)).build())
            .build();
    }
}
