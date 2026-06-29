package com.dreykaoas.lethalbreed.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Right-hand option list. One row per config option (see {@link OptionEntry} and its subclasses):
 * label + value control (toggle for booleans, edit field for numbers) + a small reset icon.
 * Auto-save: every edit fires {@code onChange(name, value)} immediately — there is no Save button.
 */
public final class OptionList extends ContainerObjectSelectionList<OptionEntry> {

    private final BiConsumer<String, String> onChange;
    private String gpuInfo = null;

    public OptionList(Minecraft mc, int width, int height, int y, BiConsumer<String, String> onChange) {
        super(mc, width, height, y, 32); // taller rows: label on top + a small description line below
        this.onChange = onChange;
    }

    /** Detected GPU string from the server, shown as the live description of the {@code useGpu} row. */
    public void setGpuInfo(String gpuInfo) {
        this.gpuInfo = gpuInfo;
    }

    public void setRows(List<ConfigScreenData.Row> rows, String category, String filter) {
        clearEntries();
        String f = filter.toLowerCase(Locale.ROOT);
        for (ConfigScreenData.Row r : rows) {
            boolean catOk = category == null || category.equals(r.category());
            boolean nameOk = f.isEmpty() || r.name().toLowerCase(Locale.ROOT).contains(f);
            if (catOk && nameOk) {
                addEntry("bool".equals(r.kind())
                        ? new BoolOptionEntry(minecraft.font, r, onChange, gpuInfo)
                        : new NumOptionEntry(minecraft.font, r, onChange, gpuInfo));
            }
        }
    }

    @Override
    public int getRowWidth() {
        return getWidth() - 12;
    }
}
