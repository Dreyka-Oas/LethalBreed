package com.dreykaoas.lethalbreed.entity.move.dispatch;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.domain.engine.DevTestConfig;
import com.dreykaoas.lethalbreed.entity.ZombiePursuit;
import com.dreykaoas.lethalbreed.entity.move.MoveMath;

import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The {@code [ClimbDbg]} trace, lifted out of {@code ZombieBrain}.
 *
 * <p>It is diagnostics, not behaviour — the only reason it lived inside the brain was the counter it needs.
 * Keeping it here means the per-tick decision path reads as decisions only, and the counter that throttles
 * the log to one line in four is this class's own business.
 *
 * <p>Gated on {@code debugClimb}, which is off on a shipped jar; the climb scenario turns it on to read the
 * trace, since {@code LB_DEV_TEST=climb} produces no verdict of its own.
 */
public final class ClimbDebug {

    /** One in four activations, so a hundred climbing zombies do not drown the log. */
    private static final int EVERY = 4;

    private int n;

    public void log(Zombie entity, ZombiePursuit p, double horizSq, double dy,
                    boolean stuck, int stuckTicks, boolean climbing) {
        if (!DevTestConfig.debugClimb || (n++ % EVERY != 0)) {
            return;
        }
        LethalBreed.LOGGER.info("[ClimbDbg] z{} y={} tgtY={} horiz={} dy={} stuck={}({}) climb={} ground={}",
                entity.getId(), MoveMath.f1(entity.getY()), MoveMath.f1(p.tgtY()),
                MoveMath.f1(Math.sqrt(horizSq)), MoveMath.f1(dy), stuck, stuckTicks, climbing,
                entity.onGround());
    }
}
