package com.dreykaoas.lethalbreed.entity.move;

import com.dreykaoas.lethalbreed.config.domain.FlowConfig;
import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * Spider wall-scale: when a flush vertical wall blocks the path to an overhead target, the zombie climbs the
 * face directly — a sustained upward velocity each tick while hugging the wall, placing NO blocks (so nothing
 * is ever left stranding it, unlike the dirt {@link PillarClimb}). Tops out onto the ledge and hops toward the
 * target, or gives up (height cap / stall) and falls back to a pillar. Locks the cardinal wall direction at
 * {@link #initiate} so the whole scale rises against one fixed face. See {@code entity-velocity-not-applying}.
 */
public final class WallClimb extends Ascent {
    private int wallDx = 0;       // locked cardinal toward the wall (one of dx/dz is 0)
    private int wallDz = 0;

    public WallClimb(SmartZombie owner) {
        super(owner);
    }

    /**
     * Begin scaling a flush wall toward an overhead target. Only starts from the ground, off cooldown, with
     * wall-climb enabled, AND when a head-high wall actually stands in the target's cardinal direction (so it
     * never triggers on open air — that's the pillar's job). Returns true when the scale started; the caller
     * falls back to the dirt pillar when it returns false.
     */
    public boolean initiate(ServerLevel level, double dx, double dz) {
        if (running || climbCd > 0 || !entity.onGround() || !FlowConfig.wallClimbEnabled) {
            return false;
        }
        // Probe the DOMINANT axis toward the target first, then the other — so a diagonal approach onto a wall
        // corner scales the face it is mostly heading into, not whichever cardinal happens to be checked first.
        int[] dir = WallProbe.pickWallDir(level, entity.blockPosition(),
                MoveMath.stepSign(dx), MoveMath.stepSign(dz), Math.abs(dx) >= Math.abs(dz));
        if (dir == null) {
            return false; // no flush wall in front → let the pillar handle this overhead
        }
        running = true;
        beginAscent();
        wallDx = dir[0];
        wallDz = dir[1];
        owner.setState(ZombieState.CLIMBING);
        return true;
    }

    /**
     * Per-tick while scaling: lift the zombie at {@link FlowConfig#wallClimbSpeed} and hug the wall. Tops out
     * (hops forward onto the ledge) once it reaches the target's height or the wall face ends; gives up on the
     * height cap or the stall watchdog, leaving a cooldown so it falls back to a pillar instead of re-scaling.
     */
    public void step(ServerLevel level, WorldAIContext ctx) {
        if (!running) {
            return;
        }
        if (!owner.isValid()) {
            running = false;
            return;
        }
        age++;

        double dyToTarget = owner.hasTarget() ? (owner.tgtY() - entity.getY()) : -1.0;
        double hx = owner.tgtX() - entity.getX();
        double hz = owner.tgtZ() - entity.getZ();
        double h = Math.sqrt(hx * hx + hz * hz);

        BlockPos p = entity.blockPosition();
        boolean wallHead = WallProbe.headWall(level, p, wallDx, wallDz);
        // Scan up to the target's height, capped by the climb's own height bound (not an arbitrary small
        // constant) — so even a window TALLER than a few blocks is seen as "wall resumes above" as long as the
        // face returns anywhere below the target. A handful of block reads per climbing zombie: negligible.
        int scanUp = Mth.clamp((int) Math.ceil(Math.max(dyToTarget, 0.0)), 2, Math.max(2, FlowConfig.maxClimbHeight));
        boolean faceEnded = WallProbe.faceEnded(level, p, wallDx, wallDz, scanUp);

        if (ProgressionConfig.debugClimb && (age % 3 == 1)) {
            LethalBreed.LOGGER.info("[ClimbDbg] z{} WALL y={} dyTgt={} horiz={} age={} risen={} wallHead={} faceEnded={}",
                    entity.getId(), MoveMath.f1(entity.getY()), MoveMath.f1(dyToTarget), MoveMath.f1(h), age,
                    MoveMath.f1(entity.getY() - startY), wallHead, faceEnded);
        }

        // Reached the target's height, or the wall face ended (clear for 2 cells up) → topped out. Hop forward
        // over the lip toward the target and stop; vanilla walking finishes the approach.
        if (!owner.hasTarget() || dyToTarget < 1.0 || faceEnded) {
            double ox = h > 0.001 ? hx / h : wallDx;
            double oz = h > 0.001 ? hz / h : wallDz;
            entity.setDeltaMovement(ox * 0.4, MoveMath.jumpVelocity(entity, 0.42), oz * 0.4);
            entity.hurtMarked = true;
            entity.setJumping(false);
            running = false;
            return;
        }

        // Stall watchdog: no height gain within climbJumpMaxAge means the scale is stuck (lip overhang, slab)
        // → give up. Height cap is the endless-wall safety. Either → cooldown, so MoveDispatch falls back to a
        // dirt pillar rather than immediately re-scaling the same face.
        boolean stalled = updateStallWatchdog();
        if (risen() >= FlowConfig.maxClimbHeight || stalled) {
            entity.setJumping(false);
            running = false;
            climbCd = FlowConfig.climbGiveUpCooldown;
            return;
        }

        // Stop navigation so a path doesn't drag the zombie off the face, then face the wall and drive the
        // scale: a small push INTO the wall keeps it flush (collision zeroes the excess) and a steady upward
        // velocity lifts it. No block ops — purely a velocity climb.
        entity.getNavigation().stop();
        MoveMath.faceHeading(entity, hx, hz);
        // Hug INTO the wall only while a solid face is at head height. Across a window/recess (face clear here
        // but resuming above) push zero horizontal so the zombie rises straight up its own column instead of
        // drifting INTO the gap — otherwise it enters the opening, loses the face and bails to a pillar.
        double hug = wallHead ? 0.1 : 0.0;
        entity.setDeltaMovement(wallDx * hug, FlowConfig.wallClimbSpeed, wallDz * hug);
        entity.hurtMarked = true;
        owner.setState(ZombieState.CLIMBING);
    }
}
