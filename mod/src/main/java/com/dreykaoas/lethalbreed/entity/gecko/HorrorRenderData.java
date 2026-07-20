package com.dreykaoas.lethalbreed.entity.gecko;

import net.minecraft.resources.Identifier;
import software.bernie.geckolib.constant.dataticket.SerializableDataTicket;

/**
 * GeckoLib render-state data tickets carrying our per-zombie values from the renderer's {@code addRenderData}
 * into the {@link software.bernie.geckolib.model.GeoModel}, the animation controllers, and the bone-adjust
 * hook. These are used purely as keys into the per-render {@code GeoRenderState} data map (add/get); they are
 * NOT synced through GeckoLib's {@code setAnimData} channel, so they are intentionally NOT registered with
 * {@code DataTickets.registerSerializable} (which would log "duplicate ticket" and buys us nothing here).
 */
public final class HorrorRenderData {
    private HorrorRenderData() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("lethalbreed", path);
    }

    /** Which model this zombie wears — index into {@code HorrorModels.IDS}. */
    public static final SerializableDataTicket<Integer> MODEL = SerializableDataTicket.ofInt(id("horror_model"));

    /** BOMBEUR belly-swell charge 0..1 (carried for a future geo bone-scale port). */
    public static final SerializableDataTicket<Float> BELLY = SerializableDataTicket.ofFloat(id("horror_belly"));

    /** The server's authoritative {@link com.dreykaoas.lethalbreed.entity.ZombieState} ordinal. */
    public static final SerializableDataTicket<Integer> STATE = SerializableDataTicket.ofInt(id("horror_state"));

    /** True if the zombie is actually translating this frame (position latch). */
    public static final SerializableDataTicket<Boolean> MOVING = SerializableDataTicket.ofBoolean(id("horror_moving"));

    /** True if off the ground / pushing off (leap/pillar hop). */
    public static final SerializableDataTicket<Boolean> AIRBORNE = SerializableDataTicket.ofBoolean(id("horror_airborne"));

    /** Limb-swing SPEED (~ground speed, 0..1) — walk cadence + ground-lock amplitude. */
    public static final SerializableDataTicket<Float> SPEED = SerializableDataTicket.ofFloat(id("horror_speed"));

    /** Limb-swing POSITION (accumulated walk distance) — drives the ground-locked leg swing so feet never slide. */
    public static final SerializableDataTicket<Float> WALK_POS = SerializableDataTicket.ofFloat(id("horror_walk_pos"));

    /** No-op; kept so callers can force class-load if desired (registration is not required). */
    public static void init() {}
}
