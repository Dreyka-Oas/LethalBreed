package com.dreykaoas.lethalbreed.entity.mood.sleep;

import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;
import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;
import com.dreykaoas.lethalbreed.entity.SmartZombie;

/**
 * What rouses a daytime sleeper, and what it does about it.
 *
 * <p>Two independent timers. The ALERT timer says "keep hunting by sight and sound, never doze"; it is
 * re-armed by any heard noise or any hit, and a merely-seen silent player never arms it. Gating on a
 * stable timer rather than a per-tick audibility test is what stops the chase-versus-doze stutter.
 * The WAKE timer is the reaction delay a sleeper takes before actually getting up, and it carries the
 * position of the noise so the woken zombie investigates the exact spot.
 */
public final class WakeSignal {

    private long alertUntil = Long.MIN_VALUE;
    private long wakeAt = Long.MIN_VALUE;
    private double x, y, z;
    private boolean hasPos = false;

    /** True while the zombie is roused and must not doze. */
    public boolean alert(long now) {
        return now < alertUntil;
    }

    /** Arm the alert timer: a hit, a noise, or a wake-up all keep the zombie hunting for a while. */
    public void rouse(long now) {
        alertUntil = now + ZombieMoodConfig.daySleepAlertTicks;
    }

    /** True once the reaction delay of a heard noise has elapsed. */
    public boolean readyToWake(long now) {
        return wakeAt != Long.MIN_VALUE && now >= wakeAt;
    }

    /** Start the reaction delay for a noise heard at this position, if one is not already running. */
    public void heard(long now, double nx, double ny, double nz) {
        x = nx;
        y = ny;
        z = nz;
        hasPos = true;
        if (wakeAt == Long.MIN_VALUE) {
            wakeAt = now + ZombieMoodConfig.daySleepWakeDelayTicks;
        }
    }

    /** Seed short-term memory with the stashed noise so the normal hunt walks the zombie over to it. */
    public void investigateLastNoise(SmartZombie owner, long now) {
        if (hasPos && TargetingConfig.targetMemoryTicks > 0) {
            owner.pursuit().rememberTarget(x, y, z, now + TargetingConfig.targetMemoryTicks);
        }
    }

    /** Disarm the reaction delay. A leftover armed wakeAt would instantly re-wake the next doze. */
    public void clear() {
        wakeAt = Long.MIN_VALUE;
        hasPos = false;
    }
}
