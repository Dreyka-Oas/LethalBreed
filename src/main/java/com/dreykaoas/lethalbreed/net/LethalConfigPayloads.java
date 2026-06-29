package com.dreykaoas.lethalbreed.net;

import com.dreykaoas.lethalbreed.config.ConfigFields;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Networking for the live config editor.
 *
 * <ul>
 *   <li>{@link OpenConfig} (S2C): server → client, carrying the encoded snapshot of every option; the
 *       client opens the GUI screen with it.</li>
 *   <li>{@link SetConfig} (C2S): client → server, a single {@code field = value} edit; the server applies
 *       it to the static config and persists to JSON. No OP gate — every player may edit everything.</li>
 * </ul>
 */
public final class LethalConfigPayloads {
    private LethalConfigPayloads() {}

    public record OpenConfig(String data) implements CustomPacketPayload {
        public static final Type<OpenConfig> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("lethalbreed", "open_config"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenConfig> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, OpenConfig::data, OpenConfig::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetConfig(String field, String value) implements CustomPacketPayload {
        public static final Type<SetConfig> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("lethalbreed", "set_config"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetConfig> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, SetConfig::field,
                        ByteBufCodecs.STRING_UTF8, SetConfig::value,
                        SetConfig::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Register payload types (both sides) and the server-side receiver. Call from the common init. */
    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(OpenConfig.TYPE, OpenConfig.CODEC);
        PayloadTypeRegistry.playC2S().register(SetConfig.TYPE, SetConfig.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SetConfig.TYPE, (payload, context) ->
                context.server().execute(() ->
                        ConfigFields.apply(payload.field(), payload.value(), true)));
    }
}
