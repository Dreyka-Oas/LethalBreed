package com.dreykaoas.lethalbreed.special;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

/**
 * Persistent per-entity attachment holding a zombie's {@link SpecialType} id. Set at spawn
 * ({@link SpecialRoller}, in finalizeSpawn — before the entity is tracked) and read by the
 * {@code SmartZombie} constructor at ENTITY_LOAD. Persistent → survives chunk unload/reload (a vanilla
 * {@code getPersistentData} doesn't exist in this mapping; Fabric's data-attachment API is the way).
 */
public final class SpecialAttachment {
    private SpecialAttachment() {}

    public static final AttachmentType<String> SPECIAL = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath("lethalbreed", "special"), Codec.STRING);

    /**
     * BOMBEUR belly-swell charge, 0..1. Transient (never persisted — a fresh zombie starts at 0) but
     * synced to tracking clients so the render-side model can inflate the {@code body} part as the
     * fuse burns. Ramped server-side in {@link SpecialBehavior}; read in the client model mixin.
     */
    public static final AttachmentType<Float> BOMBEUR_CHARGE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath("lethalbreed", "bombeur_charge"),
            builder -> builder
                    .initializer(() -> 0.0f)
                    .syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.all()));

    /** Force class-load so the attachments register during mod init. */
    public static void init() {}
}
