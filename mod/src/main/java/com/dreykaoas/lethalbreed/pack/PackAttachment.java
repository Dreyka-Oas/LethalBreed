package com.dreykaoas.lethalbreed.pack;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;

/**
 * Persistent per-entity attachment holding the id of the pack a zombie belongs to.
 *
 * <p><b>Why persistent rather than a map in the manager.</b> Chunks unload without warning — this project
 * has measured the delay at 2, 35, 272 and once over 1200 ticks across runs — and a zombie that goes to disk
 * takes nothing with it but its NBT. Without the attachment, every member caught by an unload would come
 * back an orphan, and a pack crossing a chunk border would shed most of itself. With it, the zombie carries
 * its membership to disk and re-joins on the way back.
 *
 * <p>It is <b>not</b> the live index: {@code PackState.liveIds} is, and that is a cache of runtime entity ids
 * rebuilt from this attachment, because runtime ids are reassigned on every reload. This attachment plus the
 * entity UUID are the only durable identities in the system.
 *
 * <p>Written only when membership actually changes, never per tick — an attachment write dirties the chunk.
 */
public final class PackAttachment {
    private PackAttachment() {}

    public static final AttachmentType<Long> PACK = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath("lethalbreed", "pack"), Codec.LONG);

    /** Force class-load so the attachment registers during mod init. */
    public static void init() {}
}
