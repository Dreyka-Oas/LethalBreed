package com.dreykaoas.lethalbreed.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Base class for one option row: label + description + reset icon. Concrete subclasses
 * ({@link BoolOptionEntry}, {@link NumOptionEntry}) add the value control (toggle / edit field).
 * Auto-save: every edit fires {@code onChange(name, value)} immediately — there is no Save button.
 */
public abstract class OptionEntry extends ContainerObjectSelectionList.Entry<OptionEntry> {
    protected final Font font;
    protected final ConfigScreenData.Row row;
    protected final BiConsumer<String, String> onChange;
    protected final Button reset;
    protected final String gpuInfo;
    protected String value;

    protected OptionEntry(Font font, ConfigScreenData.Row row, BiConsumer<String, String> onChange, String gpuInfo) {
        this.font = font;
        this.row = row;
        this.onChange = onChange;
        this.gpuInfo = gpuInfo;
        this.value = row.value();
        this.reset = Button.builder(Component.literal("↺"), b -> doReset())
                .bounds(0, 0, 16, 16).build();
    }

    protected abstract void doReset();

    protected void drawLabel(GuiGraphics g, int rightControlsW) {
        String label = Component.translatable("lethalbreed.option." + row.name()).getString();
        int maxW = getContentWidth() - rightControlsW - 6;
        while (font.width(label) > maxW && label.length() > 4) {
            label = label.substring(0, label.length() - 2);
        }
        g.drawString(font, label, getContentX(), getContentY() + 3, 0xFFFFFFFF);
        drawDesc(g, rightControlsW);
    }

    /** Small, dark, scaled-down description line under the label (skipped if no .desc translation). */
    private void drawDesc(GuiGraphics g, int rightControlsW) {
        String s;
        if ("useGpu".equals(row.name()) && gpuInfo != null && !gpuInfo.isEmpty()) {
            s = "GPU : " + gpuInfo; // live detected GPU instead of the static description
        } else {
            String key = "lethalbreed.option." + row.name() + ".desc";
            s = Component.translatable(key).getString();
            if (s.equals(key) || s.isEmpty()) {
                return; // no description provided
            }
        }
        float sc = 0.8f;
        int maxW = (int) ((getContentWidth() - rightControlsW - 6) / sc);
        while (font.width(s) > maxW && s.length() > 4) {
            s = s.substring(0, s.length() - 2);
        }
        g.pose().pushMatrix();
        g.pose().translate(getContentX(), getContentY() + 15f);
        g.pose().scale(sc, sc);
        g.drawString(font, s, 0, 0, 0xFF808080, false); // darker grey, no shadow
        g.pose().popMatrix();
    }

    protected void placeReset(GuiGraphics g, int mouseX, int mouseY, float partial) {
        reset.setX(getContentX() + getContentWidth() - 16);
        reset.setY(getContentY() + 2);
        reset.render(g, mouseX, mouseY, partial);
    }

    /** Full (untruncated) technical description as a hover tooltip, rendered above everything. */
    protected void maybeTooltip(GuiGraphics g, int mouseX, int mouseY, boolean hovering) {
        if (!hovering) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        // Title: localized label + (technicalName) in grey.
        lines.add(Component.translatable("lethalbreed.option." + row.name())
                .append(Component.literal(" (" + row.name() + ")").withStyle(ChatFormatting.DARK_GRAY)));
        // Description (live GPU on the useGpu row, else the static .desc translation).
        if ("useGpu".equals(row.name()) && gpuInfo != null && !gpuInfo.isEmpty()) {
            lines.add(Component.literal("GPU : " + gpuInfo).withStyle(ChatFormatting.GRAY));
        } else {
            String key = "lethalbreed.option." + row.name() + ".desc";
            String desc = Component.translatable(key).getString();
            if (!desc.equals(key) && !desc.isEmpty()) {
                lines.add(Component.literal(desc).withStyle(ChatFormatting.GRAY));
            }
        }
        // Default + type metadata line.
        lines.add(Component.translatable("lethalbreed.tooltip.meta", row.def(), row.kind())
                .withStyle(ChatFormatting.DARK_GRAY));
        g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }
}
