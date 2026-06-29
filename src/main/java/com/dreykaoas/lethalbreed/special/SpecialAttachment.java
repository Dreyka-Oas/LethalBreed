package com.dreykaoas.lethalbreed.special;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
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

    /** Force class-load so the attachment registers during mod init. */
    public static void init() {}
}
