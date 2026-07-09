package com.dreykaoas.lethalbreed.client.screen;

import com.dreykaoas.lethalbreed.config.ConfigType;
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
        this.edit.setMaxLength(row.kind().equals("list") ? 256 : 32);
        this.edit.setValue(value);
        this.edit.setResponder(text -> {
            if (ConfigType.isValidNumber(row.kind(), text)) {
                value = text.trim();
                onChange.accept(row.name(), value);
            }
        });
    }

    @Override
    protected void doReset() {
        value = row.def();
        edit.setValue(value); // fires responder → sends
    }

    @Override
    public void renderContent(GuiGraphics g, int mouseX, int mouseY, boolean hovering, float partial) {
        renderRow(g, edit, 70, mouseX, mouseY, hovering, partial);
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
