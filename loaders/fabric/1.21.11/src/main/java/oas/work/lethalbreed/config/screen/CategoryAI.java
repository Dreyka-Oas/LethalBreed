/**
 * Project: Lethal Breed
 * Responsibility: Config Category - AI
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

public class CategoryAI {
    public static ConfigCategory build(ModConfig config) {
        return ConfigCategory.createBuilder()
            .name(Text.translatable("lethalbreed.config.category.ai"))
            .option(Option.<Double>createBuilder().name(Text.translatable("lethalbreed.config.option.hearing_range")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.hearing_range.desc"))).binding(16.0, () -> config.ai.hearingRange, v -> config.ai.hearingRange = v).controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 128.0).step(1.0)).build())
            .option(Option.<Integer>createBuilder().name(Text.translatable("lethalbreed.config.option.kamikaze_fuse")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.kamikaze_fuse.desc"))).binding(40, () -> config.ai.kamikazeFuseTicks, v -> config.ai.kamikazeFuseTicks = v).controller(opt -> IntegerSliderControllerBuilder.create(opt).range(10, 200).step(1)).build())
            .option(Option.<Float>createBuilder().name(Text.translatable("lethalbreed.config.option.kamikaze_power")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.kamikaze_power.desc"))).binding(3.0f, () -> config.ai.kamikazeExplosionPower, v -> config.ai.kamikazeExplosionPower = v).controller(opt -> FloatSliderControllerBuilder.create(opt).range(1.0f, 10.0f).step(0.5f)).build())
            .option(Option.<Integer>createBuilder().name(Text.translatable("lethalbreed.config.option.sound_lock_ticks")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.sound_lock_ticks.desc"))).binding(300, () -> config.ai.soundLockTicks, v -> config.ai.soundLockTicks = v).controller(opt -> IntegerSliderControllerBuilder.create(opt).range(20, 1200).step(20)).build())
            .build();
    }
}
