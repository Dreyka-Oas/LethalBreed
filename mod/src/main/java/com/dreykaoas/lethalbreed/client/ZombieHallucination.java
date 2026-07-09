package com.dreykaoas.lethalbreed.client;

import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Optional;

/**
 * Client-only plague hallucination: while the LOCAL player is symptomatic with Super Contamination, every OTHER
 * player on screen is drawn with the zombie skin (and zombie arm pose). Purely visual, only on the sick player's
 * own client — nobody else's view changes and no packet is involved (the trigger is the local player's own synced
 * skull effect, like {@link ContaminationScreenOverlay}).
 *
 * <p>Unlike a zombie ENTITY proxy (which floated/flickered because it fought entity culling and Y interpolation),
 * this rides the vanilla player render pipeline untouched — same position, same pose, same animation — and only
 * swaps the texture and forces the classic straight-arm zombie pose. No fly, no flicker, no extra entity.
 */
@Environment(EnvType.CLIENT)
public final class ZombieHallucination {
    private ZombieHallucination() {}

    /** Zombie skin texture, our own copy: the vanilla zombie mirrors its right limbs onto the left (the left-limb
     *  UVs are blank), but the PLAYER model reads separate left-limb UVs — so on the vanilla texture a player would
     *  lose its left arm+leg. This copy has the right limbs mirrored into the left UVs so all four render. */
    public static final Identifier ZOMBIE_SKIN =
            Identifier.fromNamespaceAndPath("lethalbreed", "textures/entity/zombie_hallucination.png");

    /** Zombie body texture, built once (id + explicit texture path so we don't rely on path derivation). */
    private static final ClientAsset.ResourceTexture ZOMBIE_BODY = new ClientAsset.ResourceTexture(
            Identifier.fromNamespaceAndPath("lethalbreed", "zombie_hallucination"), ZOMBIE_SKIN);

    /** Return a copy of {@code skin} with the zombie body texture AND the WIDE model — the zombie skin uses 4px
     *  arms, so a slim (Alex) victim would otherwise lose an arm/leg to the UV mismatch. */
    public static PlayerSkin zombieSkin(PlayerSkin skin) {
        return skin.with(PlayerSkin.Patch.create(
                Optional.of(ZOMBIE_BODY), Optional.empty(), Optional.empty(),
                Optional.of(net.minecraft.world.entity.player.PlayerModelType.WIDE)));
    }

    /** True while a zombie-vision hallucination episode is active on the local player (flares on its own random
     *  timer, server-side; synced as the transient ZOMBIE_VISION effect). */
    public static boolean localSymptomatic() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && LethalBreedEffects.ZOMBIE_VISION != null
                && p.hasEffect(LethalBreedEffects.ZOMBIE_VISION);
    }

    /** True when {@code player} should be drawn as a zombie: it's another player and the local player is sick. */
    public static boolean shouldHallucinate(AbstractClientPlayer player) {
        LocalPlayer self = Minecraft.getInstance().player;
        if (self == null || player == self) {
            return false;
        }
        return localSymptomatic();
    }
}
