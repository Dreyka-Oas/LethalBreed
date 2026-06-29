package com.dreykaoas.lethalbreed.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;

/** Option row for a boolean value: an ON/OFF toggle button. */
public final class BoolOptionEntry extends OptionEntry {
    private final Button toggle;

    public BoolOptionEntry(Font font, ConfigScreenData.Row row, BiConsumer<String, String> onChange, String gpuInfo) {
        super(font, row, onChange, gpuInfo);
        this.toggle = Button.builder(label(value), b -> {
            value = "true".equalsIgnoreCase(value) ? "false" : "true";
            b.setMessage(label(value));
            onChange.accept(row.name(), value);
        }).bounds(0, 0, 50, 16).build();
    }

    private static Component label(String v) {
        return Component.literal("true".equalsIgnoreCase(v) ? "ON" : "OFF");
    }

    @Override
    protected void doReset() {
        value = row.def();
        toggle.setMessage(label(value));
        onChange.accept(row.name(), value);
    }

    @Override
    public void renderContent(GuiGraphics g, int mouseX, int mouseY, boolean hovering, float partial) {
        drawLabel(g, 16 + 4 + 50);
        int right = getContentX() + getContentWidth();
        toggle.setX(right - 16 - 4 - 50);
        toggle.setY(getContentY() + 2);
        toggle.render(g, mouseX, mouseY, partial);
        placeReset(g, mouseX, mouseY, partial);
        maybeTooltip(g, mouseX, mouseY, hovering);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(toggle, reset);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(toggle, reset);
    }
}
