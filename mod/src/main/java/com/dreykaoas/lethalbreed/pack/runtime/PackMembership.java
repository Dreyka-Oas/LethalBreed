package com.dreykaoas.lethalbreed.pack.runtime;

import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.pack.PackAttachment;
import com.dreykaoas.lethalbreed.pack.rule.PackJoinRule;
import com.dreykaoas.lethalbreed.pack.PackState;

import java.util.function.LongFunction;

/**
 * The one place a zombie's pack membership changes.
 *
 * <p>Membership is recorded in three views that must never disagree: the persistent attachment (survives
 * chunk unload), the in-memory tether on {@code ZombiePursuit} (read every activation, cheap), and the
 * pack's own {@code liveIds} roster. Letting call sites update them individually is how one of the three
 * ends up stale, and a stale roster means a pack that materialises members it does not have, or counts
 * members twice. Every transition goes through here.
 */
public final class PackMembership {
    private PackMembership() {}

    /**
     * Put a zombie in a pack, taking it out of whatever it was in first.
     *
     * @param lookup resolves a pack id to its state, so the previous pack's roster can be cleaned. Without
     *               it a re-homed zombie would stay listed in its old pack, which then counts and tries to
     *               materialise a member it does not have.
     */
    public static void join(SmartZombie sz, PackState pack, LongFunction<PackState> lookup) {
        long previous = sz.pursuit().pack().packId();
        if (previous != pack.id && previous != PackJoinRule.NO_PACK) {
            PackState old = lookup.apply(previous);
            if (old != null) {
                removeId(old, sz.id());
            }
        }
        admit(sz, pack);
    }

    /**
     * Write all three views, even when the tether already names this pack.
     *
     * <p>A tether pointing at a pack is not proof that pack's roster knows the member, and the two do come
     * apart: the same id can be handed to a new {@code PackState} (a restore, a hand-installed pack), which
     * starts with an empty roster while live members still carry the old tether. Returning early on
     * {@code previous == pack.id} left that pack reading {@code totalMembers() == 0}, so the next sweep
     * dissolved it under members that were standing right there, and each of them then went to disk with no
     * attachment left to come back through. Rewriting is idempotent and costs a contains() on a list capped
     * at {@code packMaxSize}.
     */
    private static void admit(SmartZombie sz, PackState pack) {
        if (!pack.liveIds.contains(sz.id())) {
            pack.liveIds.add(sz.id());
        }
        if (sz.pursuit().pack().packId() != pack.id) {
            // setPackId clears the stray counter, so a repair of the roster alone must not reset it.
            sz.pursuit().pack().setPackId(pack.id);
        }
        sz.entity().setAttached(PackAttachment.PACK, pack.id);
    }

    /** Take a zombie out of its pack entirely: it becomes loose and may form or join another later. */
    public static void leave(SmartZombie sz, PackState pack) {
        if (pack != null) {
            removeId(pack, sz.id());
        }
        sz.pursuit().pack().setPackId(PackJoinRule.NO_PACK);
        sz.entity().removeAttached(PackAttachment.PACK);
    }

    /**
     * Re-attach a zombie that came back from disk still carrying its pack id.
     *
     * <p>Returns false when the pack is gone, in which case the caller must clear the attachment: a zombie
     * pointing at a pack that no longer exists would keep failing to re-join on every reload, forever.
     */
    public static boolean rejoin(SmartZombie sz, PackState pack) {
        if (pack == null) {
            sz.pursuit().pack().setPackId(PackJoinRule.NO_PACK);
            sz.entity().removeAttached(PackAttachment.PACK);
            return false;
        }
        if (!pack.liveIds.contains(sz.id())) {
            pack.liveIds.add(sz.id());
        }
        // It was counted as detached while it sat on disk; it is live again now. Never below zero. A double
        // decrement would make an occupied pack read as empty and get dropped with its members still around.
        pack.detached = Math.max(0, pack.detached - 1);
        sz.pursuit().pack().setPackId(pack.id);
        return true;
    }

    /**
     * The chunk unloaded before we could snapshot this member: it went to disk carrying its attachment.
     *
     * <p>Counted as detached, with no ghost written. Creating a ghost here would mean the same
     * zombie exists both in the save file and in our snapshot list, and re-materialising it would put a
     * second copy in the world, permanently, since this mod marks every zombie persistence-required and
     * nothing ever despawns.
     */
    public static void detach(PackState pack, int entityId) {
        if (removeId(pack, entityId)) {
            pack.detached++;
        }
    }

    private static boolean removeId(PackState pack, int entityId) {
        int at = pack.liveIds.indexOf(entityId);
        if (at < 0) {
            return false;
        }
        pack.liveIds.removeInt(at);
        return true;
    }
}
