package com.dreykaoas.lethalbreed.entity;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

/**
 * Per-zombie render-model choice: the index into {@link HorrorModels#IDS} (0 = plain look, 1..15 = a horror
 * model). Rolled once at spawn in {@link ZombieVariation} (in finalizeSpawn, before the entity is tracked, so
 * the client receives the final model with no swap flicker). Persistent (a zombie keeps its model across
 * chunk reloads) AND synced to tracking clients (the GeckoLib replaced-entity renderer reads it each frame to
 * pick the model/texture/animation). Same Fabric-attachment idiom as {@code SpecialAttachment}.
 */
public final class HorrorModelAttachment {
    private HorrorModelAttachment() {}

    public static final AttachmentType<Integer> MODEL = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath("lethalbreed", "horror_model"),
            builder -> builder
                    .initializer(() -> 0)
                    .persistent(Codec.INT)
                    .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

    /**
     * The zombie's live {@link ZombieState} ordinal, set by the server AI ({@link SmartZombie#setState}) and
     * synced to the client so the render pipeline knows EXACTLY what the zombie is doing — walk vs idle vs
     * pillaring/climbing (BUILDING) — instead of guessing from client-side position/velocity, which lags and
     * arrives in packet gaps (the cause of the residual sliding). Transient (a fresh zombie starts IDLE).
     */
    public static final AttachmentType<Integer> ANIM_STATE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath("lethalbreed", "horror_anim_state"),
            builder -> builder
                    .initializer(() -> 0) // ZombieState.IDLE.ordinal()
                    .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

    /** Force class-load so the attachments register during mod init. */
    public static void init() {}
}
