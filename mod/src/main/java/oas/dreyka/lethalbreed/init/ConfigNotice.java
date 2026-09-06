package oas.dreyka.lethalbreed.init;

import oas.dreyka.lethalbreed.config.io.diag.ConfigDrift;
import oas.dreyka.lethalbreed.config.io.ConfigIo;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * The one-off warning an operator sees on join when the config file on disk carries drift the loader
 * could not repair by itself.
 *
 * <p>Only what the user has to act on: a key whose value is genuinely lost, or one written twice. Drift
 * the write that follows the read already fixed is deliberately silent, because a message about a file
 * that is already fixed is noise, and noise is what makes an operator stop reading these.
 *
 * <p>It names every offending key. This used to be a count plus {@code /lethalconfig verify}, which made
 * the reader run a command to be told the one thing the message was for; that subcommand is gone. What
 * survives here is rare by construction, so the line budget is a guard against a pathological file, not
 * an expected path.
 */
final class ConfigNotice {
    private ConfigNotice() {}

    private static final int LINES = 6;

    static void register() {
        // Tell an operator, once on join, that the config file has a structural problem. The startup
        // WARN covers dedicated-server admins who read logs; this covers everyone else, because a
        // solo player never opens latest.log and would otherwise just watch their hand-edited line
        // stop working with no explanation anywhere they look.
        //
        // Only for drift the loader could NOT repair: clean() ignores renamed typos, misplaced
        // options and stale category names, all of which the load-then-write cycle corrects by itself.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ConfigDrift.Report report = ConfigIo.lastReport();
            if (report == null || report.clean()) {
                return;
            }
            // Same gate the SetConfig packet and /lethalconfig use, only the people who can act on it.
            if (!handler.getPlayer().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                return;
            }
            ServerPlayer op = handler.getPlayer();
            op.sendSystemMessage(Component.literal("[LethalBreed] ")
                    .append(Component.translatable("lethalbreed.notice.config_problems",
                            report.problemCount()))
                    .withStyle(ChatFormatting.GOLD));

            int shown = 0;
            for (ConfigDrift.Unknown u : report.unknown()) {
                if (shown == LINES) {
                    break;
                }
                shown++;
                op.sendSystemMessage((u.suggestion() != null
                                ? Component.translatable("lethalbreed.notice.unknown_ambiguous",
                                        u.name(), u.suggestion())
                                : Component.translatable("lethalbreed.notice.unknown_option", u.name()))
                        .withStyle(ChatFormatting.RED));
            }
            for (String d : report.duplicated()) {
                if (shown == LINES) {
                    break;
                }
                shown++;
                op.sendSystemMessage(Component.translatable("lethalbreed.notice.duplicated", d)
                        .withStyle(ChatFormatting.RED));
            }
            if (report.problemCount() > shown) {
                op.sendSystemMessage(Component.translatable("lethalbreed.notice.more_problems",
                                report.problemCount() - shown)
                        .withStyle(ChatFormatting.GRAY));
            }
        });

        // NoAI-release MUST happen here, on STOPPING, not on STOPPED: Fabric fires SERVER_STOPPING at HEAD of
        // MinecraftServer.stopServer() and SERVER_STOPPED at TAIL, but stopServer() calls saveAllChunks(...)
        // (flushing every loaded zombie's NoAI to disk) and then serverLevel.close() BEFORE it returns, i.e.
        // strictly between STOPPING and STOPPED. By the time STOPPED fires, the save already happened and the
        // level is closed, so releasing the hold there is a no-op that can't reach the NBT that was just
        // written. Do NOT "tidy" this back into the SERVER_STOPPED handler below: that silently reintroduces
        // the frozen-statue bug this exists to fix (audit #2).
    }
}
