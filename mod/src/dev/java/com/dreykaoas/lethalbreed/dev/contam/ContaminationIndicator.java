package com.dreykaoas.lethalbreed.dev.contam;

import com.dreykaoas.lethalbreed.effect.contamination.ContaminationState;
import com.dreykaoas.lethalbreed.probe.DevProbe;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Dev-only live indicator of every tracked contamination victim's stage — the latent stage is invisible by
 * design, so this is the only in-game way to confirm infection state during a dev run. Players see it as an
 * action-bar tag on themselves; other victims (zombies etc.) get it as a name tag.
 *
 * <p>This is the original {@code ContaminationSymptoms.showDevIndicator}, recovered from {@code src/main} git
 * history (it used to be called once per tick, per tracked victim, from {@code ContaminationTick.tick}) and
 * reinstated here in {@code src/dev} rather than in {@code main}: {@link ContaminationState#tracked} is
 * already {@code public}, so this needs zero widening of {@code main}. Gated on the {@link DevProbe#CONTAM}
 * trace channel ({@code DevTestConfig.debugContam}), exactly like the climb and pack trace channels.
 */
public final class ContaminationIndicator {
    private ContaminationIndicator() {}

    public static void onTick(MinecraftServer server) {
        if (!DevProbe.tracing(DevProbe.CONTAM)) {
            return;
        }
        for (LivingEntity e : ContaminationState.tracked) {
            showDevIndicator(e);
        }
    }

    private static void showDevIndicator(LivingEntity e) {
        boolean sym = ContaminationState.symptomatic(e);
        Component tag = Component.literal(sym ? "[INFECTED ✦ symptomatic]" : "[INFECTED latent]")
                .withStyle(sym ? ChatFormatting.RED : ChatFormatting.GREEN);
        if (e instanceof ServerPlayer p) {
            p.displayClientMessage(tag, true); // action bar
        } else {
            e.setCustomName(tag);
            e.setCustomNameVisible(true);
        }
    }
}
