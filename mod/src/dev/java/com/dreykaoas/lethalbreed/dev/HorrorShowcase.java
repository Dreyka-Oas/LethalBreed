package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.entity.gecko.HorrorZombie;
import com.dreykaoas.lethalbreed.entity.gecko.LethalEntities;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * DEV-ONLY visual smoke test for the {@link HorrorZombie}. Runs only when the {@code LB_SHOWCASE} env var is
 * set AND we're on the client (wired in {@link DevBootstrap}). On the first loaded world it forces clear
 * midday, spawns a short frozen arc of horror zombies in front of the player, staggers a few animation
 * triggers, and saves several in-game screenshots to {@code run/screenshots/} through the vanilla Screenshot
 * API — needing no external key/window input, so it works under Wayland where synthetic input isn't available.
 * Never packaged in a production jar (dev source set) and completely inert unless the env var is present.
 */
public final class HorrorShowcase {
    private HorrorShowcase() {}

    private static int ticks = 0;
    private static boolean armed = true;
    private static boolean placed = false;
    private static final List<HorrorZombie> MOBS = new ArrayList<>();

    public static void install() {
        ClientTickEvents.END_CLIENT_TICK.register(HorrorShowcase::onTick);
        LethalBreed.LOGGER.info("[showcase] armed (LB_SHOWCASE) — will stage horror zombies + screenshot.");
    }

    private static void onTick(Minecraft mc) {
        if (!armed) {
            return;
        }
        if (mc.level == null || mc.player == null) {
            ticks = 0;
            return;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        ticks++;
        LocalPlayer p = mc.player;

        if (ticks == 40 && !placed) {
            placed = true;
            final double px = p.getX(), py = p.getY(), pz = p.getZ();
            final float yaw = p.getYRot();
            final ResourceKey<Level> dim = p.level().dimension();
            server.execute(() -> stage(server, dim, px, py, pz, yaw));
        }
        if (ticks == 46) {
            p.setXRot(-3.0f); // level the camera slightly down to frame full bodies
        }

        if (ticks == 120) {
            triggerOn(server, 1, "spasm");
        }
        if (ticks == 150) {
            triggerOn(server, 0, "attack");
        }
        if (ticks == 175) {
            triggerOn(server, 2, "attack");
        }

        if (ticks == 95 || ticks == 132 || ticks == 158 || ticks == 185) {
            Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), c -> { });
            LethalBreed.LOGGER.info("[showcase] screenshot @ tick {}", ticks);
        }
        if (ticks >= 205) {
            armed = false;
            LethalBreed.LOGGER.info("[showcase] complete — screenshots in run/screenshots/");
        }
    }

    private static void stage(IntegratedServer server, ResourceKey<Level> dim,
                              double px, double py, double pz, float yaw) {
        ServerLevel lvl = server.getLevel(dim);
        if (lvl == null) {
            lvl = server.overworld();
        }
        CommandSourceStack src = server.createCommandSourceStack();
        server.getCommands().performPrefixedCommand(src, "time set 2000");
        server.getCommands().performPrefixedCommand(src, "weather clear 1000000");
        server.getCommands().performPrefixedCommand(src, "gamerule doDaylightCycle false");

        double yawRad = Math.toRadians(yaw);
        double fx = -Math.sin(yawRad), fz = Math.cos(yawRad); // forward (MC: yaw 0 faces +Z)
        double rx = Math.cos(yawRad), rz = Math.sin(yawRad);  // player's right
        // One of EACH variant, laid out in two rows of five in front of the player, frozen and facing them.
        int n = LethalEntities.VARIANTS.size();
        double spacing = 2.6;
        for (int i = 0; i < n; i++) {
            LethalEntities.Variant variant = LethalEntities.VARIANTS.get(i);
            int col = i % 5;
            double dist = (i < 5) ? 9.0 : 13.0;
            double off = (col - 2) * spacing;
            double sx = px + fx * dist + rx * off;
            double sz = pz + fz * dist + rz * off;
            BlockPos pos = BlockPos.containing(sx, py, sz);
            HorrorZombie z = variant.type().spawn(lvl, pos, EntitySpawnReason.COMMAND);
            if (z != null) {
                z.setNoAi(true);
                z.setPersistenceRequired();
                z.absSnapTo(sx, py, sz, yaw + 180.0f, 0.0f);
                z.setYHeadRot(yaw + 180.0f);
                z.setYBodyRot(yaw + 180.0f);
                MOBS.add(z);
            }
        }
        LethalBreed.LOGGER.info("[showcase] staged {} horror zombies — one of each variant (day, frozen).", MOBS.size());
    }

    private static void triggerOn(IntegratedServer server, int idx, String anim) {
        server.execute(() -> {
            if (idx < MOBS.size() && MOBS.get(idx).isAlive()) {
                MOBS.get(idx).triggerAnim("main", anim);
            }
        });
    }
}
