package com.dreykaoas.lethalbreed.command;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * Shared chat feedback for the mod's commands: every prefixed line carries the same {@code "[LethalBreed] "}
 * tag, so the individual commands don't each re-spell the prefix and the success/failure supplier idiom.
 */
final class CommandFeedback {
    private CommandFeedback() {
    }

    private static final String PREFIX = "[LethalBreed] ";

    static void success(CommandSourceStack src, String msg, ChatFormatting style, boolean broadcast) {
        src.sendSuccess(() -> Component.literal(PREFIX + msg).withStyle(style), broadcast);
    }

    static void failure(CommandSourceStack src, String msg) {
        src.sendFailure(Component.literal(PREFIX + msg));
    }
}
