package com.dreykaoas.lethalbreed.client.screen;

import com.dreykaoas.lethalbreed.net.LethalConfigPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Custom config menu (Sodium-style): a search box across the top, a vertical category sidebar on the left,
 * and the option list on the right. Auto-save — every toggle/edit/reset is sent to the server immediately
 * (no Save/Cancel buttons; Esc closes). Each option row carries its own reset icon. Built from the server
 * snapshot string.
 */
public final class CustomConfigScreen extends Screen {

    private static final int SIDEBAR_W = 110;
    private static final int MARGIN = 8;
    private static final int SEARCH_H = 18;
    private static final int TOP = 8 + SEARCH_H + 8; // below the search box

    private final List<ConfigScreenData.Row> rows;
    private final List<String> categories = new ArrayList<>();
    private String selected = "";
    private String filter = "";

    private EditBox search;
    private CategoryList catList;
    private OptionList optList;

    private String gpuInfo = null;

    public CustomConfigScreen(String data) {
        super(Component.literal("LethalBreed Config"));
        // Optional leading "@gpu=<detected gpu>" meta line, shown live on the useGpu row.
        if (data.startsWith("@gpu=")) {
            int nl = data.indexOf('\n');
            if (nl > 0) {
                gpuInfo = data.substring(5, nl);
                data = data.substring(nl + 1);
            }
        }
        this.rows = ConfigScreenData.parse(data);
        LinkedHashSet<String> cats = new LinkedHashSet<>();
        for (ConfigScreenData.Row r : rows) {
            cats.add(r.category());
        }
        categories.addAll(cats);
        if (!categories.isEmpty()) {
            selected = categories.get(0);
        }
    }

    @Override
    protected void init() {
        // Search box — outside/above the two panels, full width.
        search = new EditBox(this.font, MARGIN, 8, this.width - 2 * MARGIN, SEARCH_H, Component.literal("search"));
        search.setHint(Component.translatable("lethalbreed.config.search_hint"));
        search.setValue(filter);
        search.setResponder(text -> {
            filter = text == null ? "" : text;
            refreshOptions();
        });
        addRenderableWidget(search);

        int listH = this.height - TOP - MARGIN;

        // Left sidebar.
        catList = new CategoryList(this.minecraft, SIDEBAR_W, listH, TOP, this::select);
        catList.setX(MARGIN);
        catList.setCategories(categories, selected);
        addRenderableWidget(catList);

        // Right options.
        int optX = MARGIN + SIDEBAR_W + MARGIN;
        int optW = this.width - optX - MARGIN;
        optList = new OptionList(this.minecraft, optW, listH, TOP, this::send);
        optList.setX(optX);
        optList.setGpuInfo(gpuInfo);
        refreshOptions();
        addRenderableWidget(optList);
    }

    private void select(String cat) {
        this.selected = cat;
        rebuildWidgets(); // refresh sidebar highlight + option list
    }

    private void refreshOptions() {
        if (optList != null) {
            optList.setRows(rows, filter.isEmpty() ? selected : null, filter); // search spans all categories
        }
    }

    /** Auto-save: push one edit to the server (applies live + persists to JSON). */
    private void send(String name, String value) {
        ClientPlayNetworking.send(new LethalConfigPayloads.SetConfig(name, value));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }
}
