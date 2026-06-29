package com.dreykaoas.lethalbreed.entity.move;

import com.dreykaoas.lethalbreed.ai.flowfield.FlowField;
import com.dreykaoas.lethalbreed.config.domain.FlowConfig;
import com.dreykaoas.lethalbreed.config.domain.SchedulerConfig;
import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombiePursuit;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Re-pathing for {@link ZombieBrain}: throttles path re-issue by LOD tier and, when a flow field covers the
 * zombie's cell, steers toward the downhill waypoint (routing around walls / through break-and-bridge cells)
 * instead of walking straight at the target. Owns the per-zombie re-issue counter and the reused direction
 * scratch, so no allocation happens on the per-tick nav path.
 */
final class BrainNavigator {
    private final SmartZombie owner;
    private final Zombie entity;

    private int sinceNav;
    private final int[] flowDir = new int[2]; // scratch for FlowField.sampleInto, reused each navTo

    BrainNavigator(SmartZombie owner) {
        this.owner = owner;
        this.entity = owner.entity();
    }

    /** Walk toward the remembered sound, returning false (and clearing it) once within arrival distance. */
    boolean navigateToSound(WorldAIContext ctx) {
        ZombiePursuit p = owner.pursuit();
        double dx = p.soundX() - entity.getX();
        double dz = p.soundZ() - entity.getZ();
        double arrive = TargetingConfig.soundArriveDistance;
        if (dx * dx + dz * dz <= arrive * arrive) {
            p.clearSound();
            return false;
        }
        navTo(ctx, p.soundX(), p.soundY(), p.soundZ());
        return true;
    }

    /** Re-path only when the previous path finished or the re-issue interval elapsed. When a flow field
     *  covers the zombie's cell, steer toward the downhill waypoint (so it routes around walls / through
     *  break-and-bridge cells); otherwise fall back to walking straight at the target. */
    void navTo(WorldAIContext ctx, double x, double y, double z) {
        PathNavigation nav = entity.getNavigation();
        // Distant zombies re-path less: a stale path costs little when far, so MEDIUM/LOW stretch the
        // re-issue interval by their multiplier. nav.isDone() still re-paths immediately for any tier.
        int mult = switch (owner.lod()) {
            case MEDIUM -> Math.max(1, SchedulerConfig.lodMediumNavMultiplier);
            case LOW -> Math.max(1, SchedulerConfig.lodLowNavMultiplier);
            default -> 1;
        };
        int reissue = Math.max(1, SchedulerConfig.navReissueInterval) * mult;
        if (nav.isDone() || sinceNav >= reissue) {
            if (!navViaFlow(ctx, nav)) {
                nav.moveTo(x, y, z, FlowConfig.navSpeed);
            }
            sinceNav = 0;
        } else {
            sinceNav++;
        }
    }

    /** Follow the dimension's flow field: from the zombie's cell, step up to {@code flowWaypointStep} cells
     *  downhill (toward the nearest player) and aim the vanilla navigation at that waypoint. Returns false
     *  (caller walks straight at the target instead) when no field is active, the zombie is outside it, or it
     *  already sits on a goal/dead cell. The waypoint Y is the zombie's own Y — vanilla nav resolves the
     *  reachable ground around it; vertical climb/dig stays driven by MoveDispatch from the real target. */
    private boolean navViaFlow(WorldAIContext ctx, PathNavigation nav) {
        FlowField field = ctx.flowFieldManager().active();
        if (field == null) {
            return false;
        }
        int cx = entity.blockPosition().getX();
        int cz = entity.blockPosition().getZ();
        if (!field.sampleInto(cx, cz, flowDir)) {
            return false; // outside the field, impassable, or already at a goal cell
        }
        int steps = Math.max(1, FlowConfig.flowWaypointStep);
        for (int s = 0; s < steps; s++) {
            if (!field.sampleInto(cx, cz, flowDir)) {
                break; // reached the goal / edge of the field — stop here
            }
            cx += flowDir[0];
            cz += flowDir[1];
        }
        // Aim at the waypoint at the zombie's own Y. If vanilla nav can't build a path to it (e.g. the
        // downhill cell sits on a cliff at a very different height), moveTo returns false and we report
        // failure so navTo() falls back to walking straight at the real target this re-issue.
        return nav.moveTo(cx + 0.5, entity.getY(), cz + 0.5, FlowConfig.navSpeed);
    }
}
