/**
 * Project: Lethal Breed
 * Responsibility: Custom O.A.S Badge Rendering for ModMenu
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.mixin.compat;

import com.terraformersmc.modmenu.util.mod.Mod;
import com.terraformersmc.modmenu.util.mod.ModBadgeRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModBadgeRenderer.class, remap = false)
public abstract class ModMenuBadgeMixin {
    @Shadow protected Mod mod;
    @Shadow protected int badgeX;
    @Shadow protected int badgeY;
    @Shadow protected MinecraftClient client;

    @Shadow public abstract void drawBadge(DrawContext context, net.minecraft.text.OrderedText text, int outlineColor, int fillColor, int x, int y);

    @Inject(method = "draw", at = @At("TAIL"))
    private void addOasBadge(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (mod != null && "lethalbreed".equals(mod.getId())) {
            Text text = Text.literal("O.A.S");
            int width = client.textRenderer.getWidth(text);
            // Saturated green badge style: Outline (solid green), Fill (deeper green)
            drawBadge(context, text.asOrderedText(), 0xFF008800, 0x99003300, badgeX, badgeY);
            badgeX += width + 6;
        }
    }
}
