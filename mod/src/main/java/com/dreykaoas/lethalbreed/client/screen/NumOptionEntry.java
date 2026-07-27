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
    /** Quiet period after the last keystroke before the value is sent. {@link EditBox#setResponder} fires on
     *  every text mutation, not on commit, so typing "36000" used to emit five SetConfig packets — each one
     *  costing the server thread two reflective 295-field scans and a blocking full-file write (~10 KB). One
     *  frame of latency is imperceptible when editing a text field; five synchronous disk writes per value
     *  are not. */
    private static final long DEBOUNCE_MS = 400L;

    private final EditBox edit;
    private String pending;          // value typed but not yet sent, or null when nothing is in flight
    private long pendingSinceMs;
    private boolean wasFocused;

    public NumOptionEntry(Font font, ConfigScreenData.Row row, BiConsumer<String, String> onChange, String gpuInfo) {
        super(font, row, onChange, gpuInfo);
        this.edit = new EditBox(font, 0, 0, 70, 16, Component.literal(row.name()));
        this.edit.setMaxLength(row.kind().equals("list") ? 256 : 32);
        this.edit.setValue(value);
        this.edit.setResponder(text -> {
            if (ConfigType.isValidNumber(row.kind(), text)) {
                pending = text.trim();
                pendingSinceMs = System.currentTimeMillis();
            }
        });
    }

    /** Send the held value, if any. Also invoked on screen close, so a value typed and immediately followed by
     *  Escape is still applied — the debounce must never be able to swallow an edit. */
    @Override
    public void flushPending() {
        if (pending == null) {
            return;
        }
        value = pending;
        pending = null;
        onChange.accept(row.name(), value);
    }

    @Override
    protected void doReset() {
        value = row.def();
        edit.setValue(value); // fires the responder, which stages the value
        flushPending();       // an explicit reset click is a commit — send it now, don't wait out the debounce
    }

    @Override
    public void renderContent(GuiGraphics g, int mouseX, int mouseY, boolean hovering, float partial) {
        // The screen has no tick hook for rows, so the debounce is driven from the render pass.
        boolean focused = edit.isFocused();
        if (pending != null
                && (!focused && wasFocused                                   // left the field → commit now
                    || System.currentTimeMillis() - pendingSinceMs >= DEBOUNCE_MS)) {
            flushPending();
        }
        wasFocused = focused;
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
