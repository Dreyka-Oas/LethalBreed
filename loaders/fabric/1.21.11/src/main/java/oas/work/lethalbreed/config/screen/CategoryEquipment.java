/**
 * Project: Lethal Breed
 * Responsibility: Config Category - Equipment
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.config.screen;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import oas.work.lethalbreed.config.ModConfig;
import net.minecraft.text.Text;

public class CategoryEquipment {
    public static ConfigCategory build(ModConfig config) {
        return ConfigCategory.createBuilder()
            .name(Text.translatable("lethalbreed.config.category.equipment"))
            .option(Option.<Float>createBuilder().name(Text.translatable("lethalbreed.config.option.kamikaze_chance")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.kamikaze_chance.desc"))).binding(0.05f, () -> config.equipment.kamikazeChance, v -> config.equipment.kamikazeChance = v).controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 1.0f).step(0.01f)).build())
            .option(Option.<Float>createBuilder().name(Text.translatable("lethalbreed.config.option.weapon_chance")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.weapon_chance.desc"))).binding(0.7f, () -> config.equipment.weaponChance, v -> config.equipment.weaponChance = v).controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 1.0f).step(0.01f)).build())
            .option(Option.<Float>createBuilder().name(Text.translatable("lethalbreed.config.option.weapon_enchant_chance")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.weapon_enchant_chance.desc"))).binding(0.4f, () -> config.equipment.weaponEnchantChance, v -> config.equipment.weaponEnchantChance = v).controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 1.0f).step(0.01f)).build())
            .option(Option.<Float>createBuilder().name(Text.translatable("lethalbreed.config.option.armor_head_chance")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.armor_head_chance.desc"))).binding(0.5f, () -> config.equipment.armorHeadChance, v -> config.equipment.armorHeadChance = v).controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 1.0f).step(0.01f)).build())
            .option(Option.<Float>createBuilder().name(Text.translatable("lethalbreed.config.option.armor_chest_chance")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.armor_chest_chance.desc"))).binding(0.4f, () -> config.equipment.armorChestChance, v -> config.equipment.armorChestChance = v).controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 1.0f).step(0.01f)).build())
            .option(Option.<Float>createBuilder().name(Text.translatable("lethalbreed.config.option.armor_legs_chance")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.armor_legs_chance.desc"))).binding(0.4f, () -> config.equipment.armorLegsChance, v -> config.equipment.armorLegsChance = v).controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 1.0f).step(0.01f)).build())
            .option(Option.<Float>createBuilder().name(Text.translatable("lethalbreed.config.option.armor_feet_chance")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.armor_feet_chance.desc"))).binding(0.4f, () -> config.equipment.armorFeetChance, v -> config.equipment.armorFeetChance = v).controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 1.0f).step(0.01f)).build())
            .option(Option.<Float>createBuilder().name(Text.translatable("lethalbreed.config.option.armor_enchant_chance")).description(OptionDescription.of(Text.translatable("lethalbreed.config.option.armor_enchant_chance.desc"))).binding(0.3f, () -> config.equipment.armorEnchantChance, v -> config.equipment.armorEnchantChance = v).controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 1.0f).step(0.01f)).build())
            .build();
    }
}
