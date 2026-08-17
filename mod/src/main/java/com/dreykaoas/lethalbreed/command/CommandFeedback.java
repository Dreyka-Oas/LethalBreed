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

    /** Same, for a message that carries a translation key.
     *
     *  <p>The prefix stays a literal — it is a brand, not a word — and only the message is translated, so
     *  each recipient reads it in their own language instead of the server's. */
    static void success(CommandSourceStack src, Component msg, ChatFormatting style, boolean broadcast) {
        src.sendSuccess(() -> Component.literal(PREFIX).append(msg).withStyle(style), broadcast);
    }

    static void failure(CommandSourceStack src, Component msg) {
        src.sendFailure(Component.literal(PREFIX).append(msg));
    }
}
