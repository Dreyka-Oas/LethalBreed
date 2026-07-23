package com.dreykaoas.lethalbreed.entity;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

/**
 * The zombie's live {@link ZombieState} ordinal, written by the server AI ({@link SmartZombie#setState}) and
 * read server-side by systems that must react to what a zombie is doing — e.g.
 * {@link com.dreykaoas.lethalbreed.mixin.ZombieSleepSilenceMixin} keeps a dozing ({@link ZombieState#SLEEPING})
 * zombie's ambient groan silent. Transient — a fresh zombie starts IDLE.
 */
public final class ZombieStateAttachment {
    private ZombieStateAttachment() {}

    public static final AttachmentType<Integer> STATE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath("lethalbreed", "zombie_state"),
            builder -> builder.initializer(() -> 0)); // ZombieState.IDLE.ordinal()

    /**
     * Whether this zombie is CURRENTLY day-sleeping ({@link ZombieState#SLEEPING}). Unlike {@link #STATE}
     * (server-only), this one is SYNCED to tracking clients so the renderer can pose the zombie as asleep
     * (arms lowered, eyes closed). Transient — a fresh zombie starts awake (false). Written on every state
     * change in {@link SmartZombie#setState}.
     */
    public static final AttachmentType<Boolean> SLEEPING = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath("lethalbreed", "sleeping"),
            builder -> builder
                    .initializer(() -> false)
                    .syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

    /** Force class-load so the attachment registers during mod init. */
    public static void init() {}
}
