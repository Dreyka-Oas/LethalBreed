package com.dreykaoas.lethalbreed.dev.arena.pack;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.pack.PackState;
import com.dreykaoas.lethalbreed.pack.runtime.PackMaterializer;
import com.dreykaoas.lethalbreed.pack.runtime.PackSnapshot;
import com.dreykaoas.lethalbreed.special.SpecialAttachment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The dematerialise/rematerialise round trip, and the one check that matters most in this whole feature:
 * that it cannot create a second copy of a member.
 *
 * <p><b>What this proves and what it does not.</b> It drives the NBT round trip and the duplicate guard
 * directly. It does <b>not</b> reproduce the real trigger — a chunk leaving the entity-ticking area — because
 * the arena is force-loaded and therefore never unloads. That path is covered by the {@code ENTITY_UNLOAD}
 * net instead, and remains unproven here; saying so is better than a check that quietly measures something
 * easier.
 */
final class PackChurnProbe {

    private final List<UUID> before = new ArrayList<>();
    private int sizeBefore;
    private int restored;
    private int duplicates;
    private int identityKept;
    private int specialKept;
    private int sizeAfter;

    /** Snapshot every live member, delete it, then bring it back — twice, the second time on purpose. */
    void run(ServerLevel ow, List<Zombie> members) {
        before.clear();
        sizeBefore = GameState.REGISTRY.size();
        List<PackState.Ghost> ghosts = new ArrayList<>();
        List<Double> healths = new ArrayList<>();
        List<String> specials = new ArrayList<>();
        for (Zombie z : members) {
            if (GameState.REGISTRY.get(z.getId()) == null) {
                continue;
            }
            PackState.Ghost ghost = PackSnapshot.capture(ow, z);
            if (ghost == null) {
                continue;
            }
            before.add(z.getUUID());
            healths.add((double) z.getMaxHealth());
            specials.add(String.valueOf(z.getAttachedOrElse(SpecialAttachment.SPECIAL, "")));
            ghosts.add(ghost);
            z.discard();
        }
        for (int i = 0; i < ghosts.size(); i++) {
            PackState.Ghost ghost = ghosts.get(i);
            Entity back = PackSnapshot.restore(ow, ghost, PackArena.CX + i * 2.0,
                    PackArena.Y, PackArena.CZ + 0.5);
            if (!(back instanceof Zombie z)) {
                continue;
            }
            restored++;
            if (z.getUUID().equals(before.get(i)) && Math.abs(z.getMaxHealth() - healths.get(i)) < 0.001) {
                identityKept++;
            }
            if (String.valueOf(z.getAttachedOrElse(SpecialAttachment.SPECIAL, "")).equals(specials.get(i))) {
                specialKept++;
            }
            // Second restore of the SAME ghost. The guard must refuse it; without the guard this is exactly
            // how a permanent duplicate is born, and nothing in this mod ever despawns one.
            if (PackMaterializer.alreadyPresent(ow, ghost)) {
                continue;
            }
            if (PackSnapshot.restore(ow, ghost, z.getX(), z.getY(), z.getZ()) != null) {
                duplicates++;
            }
        }
    }

    /** Registry size once the world has had a tick to register everything that came back. */
    void sample() {
        sizeAfter = Math.max(sizeAfter, GameState.REGISTRY.size());
    }

    int expected() {
        return before.size();
    }

    int restored() {
        return restored;
    }

    int duplicates() {
        return duplicates;
    }

    int identityKept() {
        return identityKept;
    }

    int specialKept() {
        return specialKept;
    }

    int sizeBefore() {
        return sizeBefore;
    }

    int sizeAfter() {
        return sizeAfter;
    }
}
