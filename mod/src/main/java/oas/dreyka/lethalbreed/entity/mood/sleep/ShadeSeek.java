package oas.dreyka.lethalbreed.entity.mood.sleep;

import oas.dreyka.lethalbreed.LethalBreed;
import oas.dreyka.lethalbreed.config.domain.TargetingConfig;
import oas.dreyka.lethalbreed.config.domain.ZombieMoodConfig;
import oas.dreyka.lethalbreed.entity.SmartZombie;
import oas.dreyka.lethalbreed.probe.DevProbe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The walk to shade a burning daytime sleeper takes before it can doze.
 *
 * <p>The shade is pursued as a REMEMBERED SPOT, so the full navigation (breaking and pillaring
 * included) carries the zombie there exactly like digging toward a heard noise.
 *
 * <p>All the throttling lives here because the failing case is the one that repeats:
 * {@code findShade} sweeps 8112 positions, and with no memory of a failure a stationary zombie
 * rescanned the identical volume at 1 Hz forever.
 */
public final class ShadeSeek {

    /** Breaks the one-block-short deadlock: a seek that stops closing on its target is abandoned so the
     *  search can re-plan. 60 ticks, three seconds of no progress at all, comfortably longer than a
     *  re-path or a block break and far shorter than the burn that kills an exposed zombie. */
    private final ShadeStall stall = new ShadeStall(60);
    private boolean seeking = false;
    /** Server tick before which a fresh sweep is pointless, plus where we were when it last failed. */
    private long retryAt = Long.MIN_VALUE;
    private BlockPos failedAt = null;

    /** True while walking to a shade block for a day-doze (the current memory target IS that shade). */
    public boolean seeking() {
        return seeking;
    }

    public void stop() {
        seeking = false;
    }

    /** Run one activation of the seek. */
    public void seek(ServerLevel level, Zombie entity, SmartZombie owner, long now) {
        // Master toggle: with sun-shelter off, NO shade detour exists anywhere. The exposed sleeper keeps
        // roaming in the open and simply burns. This is the second of the two ShelterFinder.findShade call
        // sites; the other is SunShelterOverride.eligible.
        if (!ZombieMoodConfig.sunShelterEnabled) {
            seeking = false;
            return;
        }
        if (owner.hasTarget()) {
            // While a target is held the brain owns the walk and this method stands back, but nothing used to
            // check the walk was still going anywhere. Measured in the headless shade rig: a zombie stopped ONE
            // BLOCK short of the roof and stood there with hasTarget and seeking both true from t+40 to t+320.
            // It never arrived, so it never dozed; it never lost the memory, so it never re-planned.
            if (seeking && stall.stalled(now, owner.pursuit().distanceToTargetSq())) {
                LethalBreed.LOGGER.debug("[LethalBreed] zombie {} abandoned a stalled shade-seek", entity.getId());
                abandon(owner, now, entity.blockPosition());
            }
            return;
        }
        if (TargetingConfig.targetMemoryTicks <= 0) {
            return; // memory routing disabled, so no shade-seek can be driven; it keeps roaming and burns
        }
        BlockPos here = entity.blockPosition();
        // Skip the sweep while the last failure is still fresh AND we have not meaningfully moved. Moving more
        // than 4 blocks exposes genuinely new volume, so that always re-arms the search immediately.
        boolean moved = failedAt == null || failedAt.distSqr(here) > 16.0;
        if (!moved && now < retryAt) {
            return;
        }
        if (DevProbe.on()) {
            DevProbe.sink.count(DevProbe.SHADE_SCAN, entity.getId());
        }
        BlockPos shade = ShelterFinder.findShade(level, here, ZombieMoodConfig.shelterSearchRadius);
        if (shade == null) {
            abandon(owner, now, here);
            return; // no shade in range, nothing to be done about the burn
        }
        failedAt = null;
        retryAt = Long.MIN_VALUE;
        stall.reset(); // a fresh target gets its full patience
        owner.pursuit().rememberTarget(shade.getX() + 0.5, shade.getY(), shade.getZ() + 0.5,
                now + TargetingConfig.targetMemoryTicks);
        seeking = true;
    }

    /**
     * Give up the current seek: drop the memory so the brain stops walking to it, and arm the retry
     * cooldown from HERE so the next sweep is throttled like any other failure. Used both when no shade
     * was found and when a found one turned out to be unreachable. From the zombie's point of view those
     * are the same outcome, and treating them differently is what let a stalled seek re-search every
     * activation.
     */
    public void abandon(SmartZombie owner, long now, BlockPos here) {
        seeking = false;
        stall.reset();
        owner.pursuit().clearMemory();
        failedAt = here;
        retryAt = now + ZombieMoodConfig.shelterRetryTicks;
    }
}
