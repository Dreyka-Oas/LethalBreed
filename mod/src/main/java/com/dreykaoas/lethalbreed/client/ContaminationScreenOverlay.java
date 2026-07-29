package com.dreykaoas.lethalbreed.client;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * A client-side screen overlay shown while the local player is SYMPTOMATIC with the Super Contamination plague
 * (the skull effect only exists in the symptomatic stage, so its presence is our trigger). The plague's LEVEL is
 * mirrored into the effect's amplifier (level-1), so the overlay reads it straight off the synced effect.
 *
 * <p>The victim's <b>periphery blurs while the centre stays sharp</b>. We run a custom post-effect
 * ({@code lethalbreed:contam_radial_blur_N}) that box-blurs a copy of the frame, then mixes sharp vs. blurred by
 * distance from the screen centre: the middle reads crisp, the edges smear. Higher plague levels swap in a chain
 * with a smaller clear radius and stronger blur, so the sharp window shrinks — vision closes in as the sickness
 * worsens.
 *
 * <p>Why one JSON per level instead of a single parametrised chain: in 1.21.11 a PostChain's custom uniforms are
 * baked into GPU buffers once at build time and cannot be set per-frame from Java. So each level ships its own
 * pre-baked chain, and we simply select which one to run.
 */
@Environment(EnvType.CLIENT)
public final class ContaminationScreenOverlay {
    private ContaminationScreenOverlay() {}

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath(LethalBreed.MOD_ID, "contam_overlay");

    /** Number of pre-baked radial-blur chains (one per plague level). Levels above this reuse the last one. */
    private static final int MAX_LEVEL = 5;

    /** Post-effect ids: assets/lethalbreed/post_effect/contam_radial_blur_N.json, one per level 1..MAX_LEVEL. */
    private static final Identifier[] CHAIN_IDS = buildChainIds();

    /** Owns the transient render targets the chain allocates each frame; reused across frames like vanilla's. */
    private static final CrossFrameResourcePool RESOURCE_POOL = new CrossFrameResourcePool(3);

    private static Identifier[] buildChainIds() {
        Identifier[] ids = new Identifier[MAX_LEVEL];
        for (int i = 0; i < MAX_LEVEL; i++) {
            ids[i] = Identifier.fromNamespaceAndPath(LethalBreed.MOD_ID, "contam_radial_blur_" + (i + 1));
        }
        return ids;
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ID,
                (GuiGraphics g, net.minecraft.client.DeltaTracker tick) -> render());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
                .register((handler, client) -> releasePool());
    }

    private static void render() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        try {
            // The plague icon stays in Creative/Spectator, but its symptoms (including this vision blur) are
            // suspended there — matches the server, which freezes all active effects while the player can't
            // take normal damage.
            if (p != null && (p.isCreative() || p.isSpectator())) {
                return;
            }
            int level = plagueLevel(p);
            if (level <= 0) {
                return;
            }

            // Pick the chain for this level (clamped): higher level = smaller clear centre, stronger edge blur.
            Identifier chainId = CHAIN_IDS[Math.min(level, MAX_LEVEL) - 1];
            PostChain chain = mc.getShaderManager().getPostChain(chainId, LevelTargetBundle.MAIN_TARGETS);
            if (chain != null) {
                chain.process(mc.getMainRenderTarget(), RESOURCE_POOL);
            }
        } finally {
            // In a finally, and therefore on the early-exit paths too. CrossFrameResourcePool.release() only
            // pushes an entry back onto the free list; endFrame() is the ONLY thing that decrements
            // framesToLive, closes the target and drops it, and there is no autonomous expiry. Skipping it on
            // the not-sick path is what kept ~33 MB of VRAM (1080p, colour + depth, x2) alive until the
            // process exited — including after returning to the menu (audit #10).
            RESOURCE_POOL.endFrame();
        }
    }

    /** Release every pooled render target outright. Called on world unload, and this is the ONLY vector it
     *  addresses: our render() is HUD-attached, so it stops being called the moment {@code level == null}, and
     *  with it endFrame() — leaving the pool's ~33 MB parked until the process exits. Vanilla needs no
     *  equivalent because GameRenderer's own endFrame() keeps sweeping at the main menu.
     *
     *  <p>NOT what fixes the stale-window-size vector, despite the obvious guess: endFrame() sweeps EVERY
     *  entry in the pool unconditionally and decrements its framesToLive — it is not gated on the entry being
     *  re-acquired that frame ({@code canUsePhysicalResource} is only consulted inside acquire()). So an entry
     *  orphaned by a resize closes on its own within framesToKeepResource+1 frames, purely from the per-frame
     *  endFrame() in render()'s finally. Vanilla's clear() inside resize() is a transient-VRAM-spike
     *  optimisation, not a correctness requirement. Do not remove that finally on the theory that this method
     *  covers for it — a disconnect never fires from a resize. */
    private static void releasePool() {
        RESOURCE_POOL.close();
    }

    /** Plague level from the synced skull effect (amplifier+1), or 0 if the player isn't symptomatic. */
    private static int plagueLevel(LocalPlayer p) {
        if (p == null || LethalBreedEffects.SUPER_CONTAMINATION == null) {
            return 0;
        }
        MobEffectInstance inst = p.getEffect(LethalBreedEffects.SUPER_CONTAMINATION);
        return inst == null ? 0 : inst.getAmplifier() + 1;
    }
}
