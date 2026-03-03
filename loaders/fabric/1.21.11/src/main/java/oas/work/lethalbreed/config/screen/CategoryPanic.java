/**
 * Project: Lethal Breed
 * Responsibility: Config Category - Panic
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.config.screen;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import oas.work.lethalbreed.config.ModConfig;
import net.minecraft.text.Text;

public class CategoryPanic {
    public static ConfigCategory build(ModConfig config) {
        return ConfigCategory.createBuilder()
            .name(Text.translatable("lethalbreed.config.category.panic"))
            .option(Option.<Float>createBuilder().name(Text.translatable("lethalbreed.config.option.health_threshold")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.health_threshold.desc"))).binding(0.25f, () -> config.panic.healthThreshold, v -> config.panic.healthThreshold = v).controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.05f, 0.9f).step(0.05f)).build())
            .option(Option.<Float>createBuilder().name(Text.translatable("lethalbreed.config.option.continue_health_threshold")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.continue_health_threshold.desc"))).binding(0.5f, () -> config.panic.continueHealthThreshold, v -> config.panic.continueHealthThreshold = v).controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.1f, 1.0f).step(0.05f)).build())
            .option(Option.<Integer>createBuilder().name(Text.translatable("lethalbreed.config.option.scream_interval")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.scream_interval.desc"))).binding(40, () -> config.panic.screamIntervalTicks, v -> config.panic.screamIntervalTicks = v).controller(opt -> IntegerSliderControllerBuilder.create(opt).range(10, 200).step(1)).build())
            .option(Option.<Double>createBuilder().name(Text.translatable("lethalbreed.config.option.alert_range")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.alert_range.desc"))).binding(12.0, () -> config.panic.allyAlertRange, v -> config.panic.allyAlertRange = v).controller(opt -> DoubleSliderControllerBuilder.create(opt).range(4.0, 64.0).step(1.0)).build())
            .option(Option.<Integer>createBuilder().name(Text.translatable("lethalbreed.config.option.stop_pack_size")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.stop_pack_size.desc"))).binding(5, () -> config.panic.stopPackSize, v -> config.panic.stopPackSize = v).controller(opt -> IntegerSliderControllerBuilder.create(opt).range(1, 20).step(1)).build())
            .option(Option.<Integer>createBuilder().name(Text.translatable("lethalbreed.config.option.cooldown_ticks")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.cooldown_ticks.desc"))).binding(600, () -> config.panic.cooldownTicks, v -> config.panic.cooldownTicks = v).controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 2400).step(20)).build())
            .option(Option.<Double>createBuilder().name(Text.translatable("lethalbreed.config.option.flee_explosion_range")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.flee_explosion_range.desc"))).binding(8.0, () -> config.panic.fleeExplosionRange, v -> config.panic.fleeExplosionRange = v).controller(opt -> DoubleSliderControllerBuilder.create(opt).range(2.0, 32.0).step(1.0)).build())
            .build();
    }
}
