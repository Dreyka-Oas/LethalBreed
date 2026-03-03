/**
 * Project: Lethal Breed
 * Responsibility: Config Category - Attributes
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.config.screen;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import oas.work.lethalbreed.config.ModConfig;
import net.minecraft.text.Text;

public class CategoryAttributes {
    public static ConfigCategory build(ModConfig config) {
        return ConfigCategory.createBuilder()
            .name(Text.translatable("lethalbreed.config.category.attributes"))
            .option(Option.<Double>createBuilder().name(Text.translatable("lethalbreed.config.option.follow_range")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.follow_range.desc"))).binding(18.0, () -> config.attributes.zombieFollowRange, v -> config.attributes.zombieFollowRange = v).controller(opt -> DoubleSliderControllerBuilder.create(opt).range(4.0, 128.0).step(1.0)).build())
            .option(Option.<Double>createBuilder().name(Text.translatable("lethalbreed.config.option.min_scale")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.min_scale.desc"))).binding(0.85, () -> config.attributes.minScale, v -> config.attributes.minScale = v).controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.1, 5.0).step(0.05)).build())
            .option(Option.<Double>createBuilder().name(Text.translatable("lethalbreed.config.option.max_scale")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.max_scale.desc"))).binding(1.35, () -> config.attributes.maxScale, v -> config.attributes.maxScale = v).controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.1, 5.0).step(0.05)).build())
            .option(Option.<Double>createBuilder().name(Text.translatable("lethalbreed.config.option.min_speed")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.min_speed.desc"))).binding(0.18, () -> config.attributes.minSpeed, v -> config.attributes.minSpeed = v).controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.05, 1.0).step(0.01)).build())
            .option(Option.<Double>createBuilder().name(Text.translatable("lethalbreed.config.option.max_speed")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.max_speed.desc"))).binding(0.28, () -> config.attributes.maxSpeed, v -> config.attributes.maxSpeed = v).controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.05, 1.0).step(0.01)).build())
            .option(Option.<Double>createBuilder().name(Text.translatable("lethalbreed.config.option.health_min")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.health_min.desc"))).binding(0.8, () -> config.attributes.healthBonusMin, v -> config.attributes.healthBonusMin = v).controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.1, 10.0).step(0.1)).build())
            .option(Option.<Double>createBuilder().name(Text.translatable("lethalbreed.config.option.health_max")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.health_max.desc"))).binding(1.2, () -> config.attributes.healthBonusMax, v -> config.attributes.healthBonusMax = v).controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.1, 10.0).step(0.1)).build())
            .build();
    }
}
