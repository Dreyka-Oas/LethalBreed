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
 * While the local player carries the "La Fin ?" plague (Super Contamination), give the HUD hearts and food icons
 * a faint green sickly tint. Purely cosmetic and deliberately subtle: we keep the vanilla sprites and just draw
 * them through a low-alpha green colour multiplier (blitSprite's ARGB tint arg) — no texture swap, no cracks. Empty
 * containers/background stay untouched so the bar still reads normally.
 */
@Environment(EnvType.CLIENT)
@Mixin(Gui.class)
public class GuiContaminationHudMixin {
    /** Green multiplier (ARGB) applied to filled icons. Full alpha, RGB near-white with a slight green bias so
     *  the sprite keeps its colour and only reads faintly sickly (red channel pulled down a touch). */
    private static final int TINT = 0xFF_D8FFD8;

    @Redirect(method = "renderHeart",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void lethalbreed$greenHeart(GuiGraphics g, RenderPipeline pipe, Identifier sprite,
                                        int x, int y, int w, int h) {
        lethalbreed$blit(g, pipe, sprite, x, y, w, h);
    }

    @Redirect(method = "renderFood",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void lethalbreed$greenFood(GuiGraphics g, RenderPipeline pipe, Identifier sprite,
                                       int x, int y, int w, int h) {
        lethalbreed$blit(g, pipe, sprite, x, y, w, h);
    }

    private static void lethalbreed$blit(GuiGraphics g, RenderPipeline pipe, Identifier sprite,
                                         int x, int y, int w, int h) {
        String path = sprite.getPath();
        // Only tint FILLED icons; leave empty containers/background vanilla (and only while contaminated).
        if (!lethalbreed$contaminated() || path.contains("empty") || path.contains("container")) {
            g.blitSprite(pipe, sprite, x, y, w, h);
            return;
        }
        // blitSprite's trailing int is an ARGB colour multiplier — draw the vanilla sprite tinted sickly green.
        g.blitSprite(pipe, sprite, x, y, w, h, TINT);
    }

    private static boolean lethalbreed$contaminated() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        return p != null && LethalBreedEffects.isSuperContaminated(p);
    }
}
