package com.dreykaoas.lethalbreed.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/** Left sidebar: one button per category, vertical. Clicking selects it (the selected one is disabled). */
public final class CategoryList extends ContainerObjectSelectionList<CategoryList.CatEntry> {

    private final Consumer<String> onSelect;

    public CategoryList(Minecraft mc, int width, int height, int y, Consumer<String> onSelect) {
        super(mc, width, height, y, 22);
        this.onSelect = onSelect;
    }

    public void setCategories(List<String> cats, String selected) {
        clearEntries();
        for (String c : cats) {
            addEntry(new CatEntry(c, c.equals(selected), onSelect));
        }
    }

    @Override
    public int getRowWidth() {
        return getWidth() - 8;
    }

    public static final class CatEntry extends ContainerObjectSelectionList.Entry<CatEntry> {
        private final Button button;

        public CatEntry(String cat, boolean selected, Consumer<String> onSelect) {
            this.button = Button.builder(Component.translatable("lethalbreed.category." + cat), b -> onSelect.accept(cat))
                    .bounds(0, 0, 100, 20).build();
            this.button.active = !selected; // selected one greyed/disabled
        }

        @Override
        public void renderContent(GuiGraphics g, int mouseX, int mouseY, boolean hovering, float partial) {
            button.setX(getContentX());
            button.setY(getContentY() + 1);
            button.setWidth(getContentWidth());
            button.render(g, mouseX, mouseY, partial);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(button);
        }
    }
}
