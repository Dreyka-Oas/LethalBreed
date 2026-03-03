/**
 * Project: Lethal Breed
 * Responsibility: Config Category - Breaking
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.config.screen;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import oas.work.lethalbreed.config.ModConfig;
import net.minecraft.text.Text;

public class CategoryBreaking {
    public static ConfigCategory build(ModConfig config) {
        return ConfigCategory.createBuilder()
            .name(Text.translatable("lethalbreed.config.category.breaking"))
            .option(Option.<Float>createBuilder().name(Text.translatable("lethalbreed.config.option.break_multiplier")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.break_multiplier.desc"))).binding(4.0f, () -> config.breaking.breakSpeedMultiplier, v -> config.breaking.breakSpeedMultiplier = v).controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.1f, 20.0f).step(0.5f)).build())
            .option(Option.<Integer>createBuilder().name(Text.translatable("lethalbreed.config.option.min_break_ticks")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.min_break_ticks.desc"))).binding(5, () -> config.breaking.breakMinTicks, v -> config.breaking.breakMinTicks = v).controller(opt -> IntegerSliderControllerBuilder.create(opt).range(1, 100).step(1)).build())
            .build();
    }
}
