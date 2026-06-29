package com.dreykaoas.lethalbreed.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;

/** Option row for a numeric value: an edit field validated against the row's kind (int/long/double). */
public final class NumOptionEntry extends OptionEntry {
    private final EditBox edit;

    public NumOptionEntry(Font font, ConfigScreenData.Row row, BiConsumer<String, String> onChange, String gpuInfo) {
        super(font, row, onChange, gpuInfo);
        this.edit = new EditBox(font, 0, 0, 70, 16, Component.literal(row.name()));
        this.edit.setMaxLength(32);
        this.edit.setValue(value);
        this.edit.setResponder(text -> {
            if (isValid(text)) {
                value = text.trim();
                onChange.accept(row.name(), value);
            }
        });
    }

    private boolean isValid(String t) {
        try {
            if (row.kind().equals("int")) Integer.parseInt(t.trim());
            else if (row.kind().equals("long")) Long.parseLong(t.trim());
            else Double.parseDouble(t.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    protected void doReset() {
        value = row.def();
        edit.setValue(value); // fires responder → sends
    }

    @Override
    public void renderContent(GuiGraphics g, int mouseX, int mouseY, boolean hovering, float partial) {
        drawLabel(g, 16 + 4 + 70);
        int right = getContentX() + getContentWidth();
        edit.setX(right - 16 - 4 - 70);
        edit.setY(getContentY() + 2);
        edit.render(g, mouseX, mouseY, partial);
        placeReset(g, mouseX, mouseY, partial);
        maybeTooltip(g, mouseX, mouseY, hovering);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(edit, reset);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(edit, reset);
    }
}
