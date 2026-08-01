package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;

import java.util.UUID;

/**
 * Synthetic player presence for the headless verification harnesses.
 *
 * <p><b>Why this is the keystone.</b> A dedicated server run by {@code gradlew runServer} has nobody
 * connected, and {@code FlowFieldManager.tick} bails the instant its targetable-player list is empty:
 * {@code active.set(null); return;}. With no flow field the zombies never path, never break, never bridge —
 * so a climbing/breaking/bridging rig produces exactly zero telemetry while <em>looking</em> like a clean
 * run. (Observed: the existing ClimbTest logs its arena-setup line and then not a single {@code [ClimbDbg]}
 * for the whole run.) Every arena harness therefore needs a player in {@code level.players()} before it
 * means anything.
 *
 * <p><b>Why {@link FakePlayer} is safe to leave standing.</b> Fabric's {@code FakePlayer} overrides
 * {@code tick()} to a no-op (so it never falls, starves, or drifts), returns {@code true} from
 * {@code isInvulnerableTo} (so it cannot burn to death mid-run and silently take the flow field with it),
 * and carries a {@code FakePlayerNetworkHandler} whose {@code send} is a no-op (so no null-connection NPE
 * when vanilla tries to push a packet at it).
 *
 * <p><b>Why {@code addFreshEntity} is enough to land in {@code level.players()}.</b>
 * {@code ServerLevel.addFreshEntity} routes a {@code ServerPlayer} through {@code addPlayer} →
 * {@code entityManager.addNewEntity}; {@code Player.isAlwaysTicking()} is {@code true}, so
 * {@code getEffectiveStatus} returns {@code TICKING} regardless of chunk state and {@code startTracking}
 * fires immediately → {@code EntityCallbacks.onTrackingStart} → {@code ServerLevel.this.players.add}. The
 * same callback also hands the player to {@code ChunkMap.addEntity}, which registers it with the
 * {@code DistanceManager} — so the fake player additionally keeps its own chunks loaded like a real one.
 * {@link #spawn} asserts the outcome rather than trusting this chain.
 *
 * <p><b>Game mode.</b> {@code Players.isTargetable} (used by both {@code FlowFieldManager} and
 * {@code TargetSelector.isValid}) rejects creative and spectator players unless
 * {@code FlowConfig.targetCreativePlayers} is set. The fake player is therefore forced to
 * {@link GameType#SURVIVAL} — otherwise a server whose {@code gamemode} property is creative would produce a
 * player that is present in {@code level.players()} and still invisible to every zombie.
 */
public final class DevFakePlayer {
    private DevFakePlayer() {}

    /** Stable UUID slot for every synthetic player this class makes (see the FakePlayer javadoc on profiles). */
    private static final UUID UUID_SLOT = UUID.fromString("11111111-2222-3333-4444-555555555555");
    /** Bumped per spawn so the profile — and therefore FakePlayer's (world, profile) cache key — is fresh.
     *  Entity.unsetRemoved() is protected, so a despawned instance can never be revived; minting a new profile
     *  is the only way a harness can legitimately spawn → despawn → spawn inside one server lifetime. */
    private static int generation = 0;
    /** The most recent player this class spawned, so {@link #despawn(ServerLevel)} can find it without a handle. */
    private static FakePlayer last = null;

    private static GameProfile profile() {
        return new GameProfile(UUID_SLOT, "[LB-Verify" + generation + "]");
    }

    /**
     * Put a synthetic player into {@code level} at ({@code x}, {@code y}, {@code z}), in SURVIVAL, present in
     * {@code level.players()}. Each call mints a fresh profile generation, so a harness may legitimately
     * spawn → {@link #despawn} → spawn again within one server lifetime; call it once per harness run.
     *
     * @return the fake player, or {@code null} if it could not be made present (the caller must treat that as
     *         a harness failure, not as a pass — an absent player means no flow field and no telemetry).
     */
    public static FakePlayer spawn(ServerLevel level, double x, double y, double z) {
        generation++;
        FakePlayer fp = FakePlayer.get(level, profile());
        last = fp;
        fp.setPos(x, y, z);
        fp.setDeltaMovement(0, 0, 0);
        fp.setNoGravity(true);      // tick() is a no-op so it would not fall anyway — belt and braces
        fp.setInvulnerable(true);
        fp.setHealth(fp.getMaxHealth());
        // SURVIVAL is required, not cosmetic: Players.isTargetable excludes creative/spectator, and a
        // non-targetable player is filtered out of FlowFieldManager's player list — i.e. no field at all.
        if (fp.gameMode() != GameType.SURVIVAL) {
            fp.setGameMode(GameType.SURVIVAL);
        }

        if (!level.players().contains(fp)) {
            level.addFreshEntity(fp);
        }
        fp.setPos(x, y, z); // re-apply after the add, so the tracked section matches the requested column

        boolean present = level.players().contains(fp);
        LethalBreed.LOGGER.info("[LB-FakePlayer] spawn @({}, {}, {}) dim={} present={} players={} mode={}",
                String.format(java.util.Locale.ROOT, "%.1f", x),
                String.format(java.util.Locale.ROOT, "%.1f", y),
                String.format(java.util.Locale.ROOT, "%.1f", z),
                level.dimension().identifier(), present, level.players().size(), fp.gameMode());
        if (!present) {
            LethalBreed.LOGGER.error("[LB-FakePlayer] NOT present in level.players() — no flow field will be "
                    + "built, so any arena harness result on this run is meaningless.");
            return null;
        }
        return fp;
    }

    /** Remove the synthetic player from the level. Safe to call when it was never spawned. */
    public static void despawn(ServerLevel level, FakePlayer fp) {
        if (fp == null) {
            return;
        }
        fp.remove(Entity.RemovalReason.DISCARDED);
        if (fp == last) {
            last = null;
        }
        LethalBreed.LOGGER.info("[LB-FakePlayer] despawned; players={}", level.players().size());
    }

    /** Remove the most recent synthetic player this class spawned (if any). */
    public static void despawn(ServerLevel level) {
        despawn(level, last);
    }
}
