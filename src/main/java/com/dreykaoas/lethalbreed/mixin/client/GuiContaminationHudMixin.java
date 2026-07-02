package com.dreykaoas.lethalbreed.mixin.client;

import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * While the local player carries the "La Fin ?" plague (Super Contamination), recolour the HUD hearts and food
 * icons green with cracks. Purely cosmetic client-side swap: each vanilla {@code blitSprite} for a filled
 * heart/food icon is redirected to blit our green cracked texture instead. Empty containers stay vanilla so the
 * bar background reads normally.
 */
@Environment(EnvType.CLIENT)
@Mixin(Gui.class)
public class GuiContaminationHudMixin {
    private static final Identifier HEART_FULL = tex("heart_full");
    private static final Identifier HEART_HALF = tex("heart_half");
    private static final Identifier FOOD_FULL = tex("food_full");
    private static final Identifier FOOD_HALF = tex("food_half");

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("lethalbreed", "textures/gui/contam/" + name + ".png");
    }

    @Redirect(method = "renderHeart",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void lethalbreed$greenHeart(GuiGraphics g, RenderPipeline pipe, Identifier sprite,
                                        int x, int y, int w, int h) {
        lethalbreed$blit(g, pipe, sprite, x, y, w, h, HEART_FULL, HEART_HALF);
    }

    @Redirect(method = "renderFood",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void lethalbreed$greenFood(GuiGraphics g, RenderPipeline pipe, Identifier sprite,
                                       int x, int y, int w, int h) {
        lethalbreed$blit(g, pipe, sprite, x, y, w, h, FOOD_FULL, FOOD_HALF);
    }

    private static void lethalbreed$blit(GuiGraphics g, RenderPipeline pipe, Identifier sprite,
                                         int x, int y, int w, int h, Identifier full, Identifier half) {
        String path = sprite.getPath();
        // Only swap FILLED icons; leave empty containers/background alone (and only while contaminated).
        if (!lethalbreed$contaminated() || path.contains("empty") || path.contains("container")) {
            g.blitSprite(pipe, sprite, x, y, w, h);
            return;
        }
        Identifier repl = path.contains("half") ? half : full;
        g.blit(pipe, repl, x, y, 0.0f, 0.0f, w, h, w, h);
    }

    private static boolean lethalbreed$contaminated() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        return p != null && p.hasEffect(LethalBreedEffects.SUPER_CONTAMINATION);
    }
}
