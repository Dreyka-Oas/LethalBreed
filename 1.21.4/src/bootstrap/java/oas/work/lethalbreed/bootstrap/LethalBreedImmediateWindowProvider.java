package oas.work.lethalbreed.bootstrap;

import com.mojang.blaze3d.platform.Monitor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraftforge.client.loading.NoVizFallback;
import net.minecraftforge.fml.loading.ImmediateWindowProvider;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class LethalBreedImmediateWindowProvider implements ImmediateWindowProvider {
    @Override
    public String name() {
        return "lethalbreednoviz";
    }

    @Override
    public Runnable initialize(String[] args) {
        return () -> { };
    }

    @Override
    public void updateFramebufferSize(IntConsumer width, IntConsumer height) {
    }

    @Override
    public long setupMinecraftWindow(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor) {
        return NoVizFallback.windowHandoff(width, height, title, monitor).getAsLong();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter, IntConsumer heightSetter, IntConsumer xSetter, IntConsumer ySetter) {
        return NoVizFallback.windowPositioning((Optional<Monitor>) (Optional<?>) monitor, widthSetter, heightSetter, xSetter, ySetter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade) {
        Supplier<LoadingOverlay> overlay = NoVizFallback.loadingOverlay((Supplier<Minecraft>) mc, (Supplier<ReloadInstance>) ri, ex, fade);
        return () -> (T) overlay.get();
    }

    @Override
    public void updateModuleReads(ModuleLayer layer) {
    }

    @Override
    public void periodicTick() {
    }

    @Override
    public String getGLVersion() {
        return NoVizFallback.glVersion();
    }
}
