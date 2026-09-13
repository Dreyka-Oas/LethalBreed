package oas.dreyka.lethalbreed.client;

import net.minecraft.util.Mth;

/**
 * The shape of a latched zombie: limbs clamped around the prey it came down on, head worrying at it. Every
 * number the pose needs lives here, away from {@code ZombieClingPoseMixin}, for the reason
 * {@code BomberBellySmoothingMath} lives away from its own mixin: a mixin class cannot be loaded by a test
 * (it names remapped Minecraft internals), and these are worth pinning.
 *
 * <p>The rotations are constants, one per limb, and only the head moves: the bite is a slow nod, and a
 * second wave a third slower rolls the head so the two never line up and the loop never reads as a metronome.
 * {@code ageInTicks} is the clock, which is the entity's own age plus the frame's partial tick, so the
 * motion is smooth between server updates and two zombies latched at different moments are out of phase.
 */
public final class ClingPose {
    private ClingPose() {}

    /** Arms angled down and forward, past vertical, to close around what is underneath. */
    public static final float ARM_X = 0.55f;
    /** Arms brought toward the centreline so the hands meet rather than hang at the sides. */
    public static final float ARM_Z = 0.62f;
    /** Knees drawn up, the grip of something that has stopped walking and is holding on. */
    public static final float LEG_X = -1.25f;
    /** Legs splayed outward from that tuck, gripping around instead of dangling straight. */
    public static final float LEG_Z = 0.3f;

    /** Where the head sits between bites: bowed into the prey. */
    private static final float HEAD_X_BASE = 0.45f;
    /** How far the nod carries, either side of {@link #HEAD_X_BASE}. */
    private static final float BITE_DEPTH = 0.22f;
    /** Radians of clock per tick for the nod: a full bite roughly every eleven ticks. */
    private static final float BITE_RATE = 0.55f;
    /** How far the head rolls with the worrying. */
    private static final float WORRY_DEPTH = 0.12f;
    /** Rate of the roll. Deliberately not a whole fraction of {@link #BITE_RATE}: the two beat against each
     *  other instead of repeating together. */
    private static final float WORRY_RATE = 0.37f;

    /** Head pitch this frame: bowed into the prey, nodding as it bites. */
    public static float headPitch(float ageInTicks) {
        return HEAD_X_BASE + Mth.sin(ageInTicks * BITE_RATE) * BITE_DEPTH;
    }

    /** Head roll this frame: the sideways worrying of a mouth that will not let go. */
    public static float headRoll(float ageInTicks) {
        return Mth.sin(ageInTicks * WORRY_RATE) * WORRY_DEPTH;
    }
}
