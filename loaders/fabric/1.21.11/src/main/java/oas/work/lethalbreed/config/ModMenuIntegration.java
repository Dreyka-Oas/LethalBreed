/**
 * Project: Lethal Breed
 * Responsibility: ModMenu and YACL Configuration Screen Integration
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import net.minecraft.text.Text;
import oas.work.lethalbreed.config.screen.*;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ModConfig config = ModConfig.INSTANCE;
            return YetAnotherConfigLib.createBuilder()
                .title(Text.translatable("lethalbreed.config.title"))
                .save(() -> ModConfig.save(config))
                .category(CategoryAttributes.build(config))
                .category(CategoryMutant.build(config))
                .category(CategoryEquipment.build(config))
                .category(CategoryAI.build(config))
                .category(CategoryPanic.build(config))
                .category(CategoryMovement.build(config))
                .category(CategoryBreaking.build(config))
                .build().generateScreen(parent);
        };
    }
}
