package com.dreykaoas.lethalbreed.pack.runtime;

import com.dreykaoas.lethalbreed.pack.PackState;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * The NBT round trip for one dematerialised pack member.
 *
 * <p><b>Why full serialisation and not a respawn.</b> Re-spawning a zombie and re-rolling its variation would
 * silently change it. {@code ZombieVariation} seeds every draw on the entity UUID, and a fresh
 * {@code EntityType.ZOMBIE.spawn()} gets a fresh UUID — so size, speed, health, effects <em>and the special
 * type</em> would all be re-drawn, and a Juggernaut could come back a Bomber. Worse, {@code applyPhase} reads
 * {@code PhaseManager.current()} at call time: a pack dematerialised in phase 3 and restored in phase 7 would
 * return with phase-7 stats it never earned. The NBT carries the UUID, so it carries the identity, so it
 * carries all of that unchanged. Fabric's persistent attachments ride along in the same tags, which is how
 * {@code lethalbreed:special}, the pack id and the contamination state survive too.
 *
 * <p>Stored as gzipped bytes rather than a live {@code CompoundTag}: it keeps {@link PackState} free of any
 * Minecraft type, and a dematerialised member is written far more often than it is read.
 */
public final class PackSnapshot {
    private PackSnapshot() {}

    /**
     * Serialise a zombie into a ghost. Returns null if the entity refuses to write — never a half-written
     * ghost, because a ghost that cannot be restored is a member silently deleted from the pack.
     */
    public static PackState.Ghost capture(ServerLevel level, Zombie zombie) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        zombie.saveWithoutId(out);
        CompoundTag tag = out.buildResult();
        // saveWithoutId omits the entity id, which loadEntityRecursive needs to know what to build.
        tag.putString("id", EntityType.getKey(zombie.getType()).toString());
        byte[] bytes = compress(tag);
        if (bytes == null) {
            return null;
        }
        UUID uuid = zombie.getUUID();
        return new PackState.Ghost(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits(), bytes);
    }

    /**
     * Rebuild a ghost at the given position and add it to the world. Returns null when the tag cannot be
     * read back or the entity type no longer exists — the caller keeps the ghost rather than losing it.
     *
     * <p>The position is overwritten <b>after</b> the load: the saved coordinates are where the pack was when
     * it dematerialised, which may be hundreds of blocks behind where it has since travelled.
     */
    public static Entity restore(ServerLevel level, PackState.Ghost ghost, double x, double y, double z) {
        CompoundTag tag = decompress(ghost.nbt());
        if (tag == null) {
            return null;
        }
        ValueInput in = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag);
        Entity entity = EntityType.loadEntityRecursive(in, level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
        if (entity == null) {
            return null;
        }
        entity.snapTo(x, y, z, entity.getYRot(), entity.getXRot());
        if (!level.addFreshEntity(entity)) {
            entity.discard();
            return null;
        }
        return entity;
    }

    /** The UUID a ghost was captured under, so the caller can ask the world whether it is already back. */
    public static UUID uuidOf(PackState.Ghost ghost) {
        return new UUID(ghost.uuidMsb(), ghost.uuidLsb());
    }

    private static byte[] compress(CompoundTag tag) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(2048)) {
            NbtIo.writeCompressed(tag, bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            // Swallowing this would turn a serialisation failure into a member that simply vanishes.
            throw new IllegalStateException("could not serialise a pack member", e);
        }
    }

    private static CompoundTag decompress(byte[] bytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new IllegalStateException("could not read back a pack member", e);
        }
    }
}
