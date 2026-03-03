/**
 * Project: Lethal Breed
 * Responsibility: Config Category - Movement
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.config.screen;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import oas.work.lethalbreed.config.ModConfig;
import net.minecraft.text.Text;

public class CategoryMovement {
    public static ConfigCategory build(ModConfig config) {
        return ConfigCategory.createBuilder()
            .name(Text.translatable("lethalbreed.config.category.movement"))
            .option(Option.<Double>createBuilder().name(Text.translatable("lethalbreed.config.option.climb_vertical")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.climb_vertical.desc"))).binding(0.25, () -> config.movement.climbVerticalSpeed, v -> config.movement.climbVerticalSpeed = v).controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.01, 1.0).step(0.01)).build())
            .option(Option.<Double>createBuilder().name(Text.translatable("lethalbreed.config.option.climb_horizontal")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.climb_horizontal.desc"))).binding(0.15, () -> config.movement.climbHorizontalSpeed, v -> config.movement.climbHorizontalSpeed = v).controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.01, 1.0).step(0.01)).build())
            .option(Option.<Integer>createBuilder().name(Text.translatable("lethalbreed.config.option.build_cooldown")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.build_cooldown.desc"))).binding(4, () -> config.movement.buildGlobalCooldownTicks, v -> config.movement.buildGlobalCooldownTicks = v).controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 100).step(1)).build())
            .build();
    }
}
