package oas.dreyka.lethalbreed.entity.move;

import oas.dreyka.lethalbreed.block.MaterialRegistry;
import oas.dreyka.lethalbreed.config.domain.CombatMoveConfig;
import oas.dreyka.lethalbreed.dimension.WorldAiContext;
import oas.dreyka.lethalbreed.entity.SmartZombie;
import oas.dreyka.lethalbreed.entity.ZombieState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The thin floor block that keeps a body from fitting under a low ceiling, and the request to take it away.
 *
 * <p>A room built two levels high leaves a 1.95 tall zombie five hundredths of a block of headroom. Carpet is
 * a sixteenth of a block thick and, unlike a snow layer, carries a real collision box, so a carpeted cell in
 * such a room no longer holds the body at all. Vanilla then refuses the horizontal move, {@code MoveControl}
 * reads the lip ahead as an obstacle and jumps, the jump meets the lid, and the zombie hops on the spot for
 * good. Measured on the gait corridor: 0.20 blocks covered against 39.20 for the same corridor and the same
 * lid without the carpet.
 *
 * <p>Reached only from the stuck branch of the dispatcher, which is what keeps it from eating decoration. A
 * zombie crossing the same carpet in a room one level taller never stops making progress, so it never asks.
 */
public final class Clearance {
    private Clearance() {
    }

    /** How far up a column is searched for a lid. Past a body's own height a ceiling cannot be pinning it. */
    private static final int CEILING_SCAN = 4;

    /**
     * Ask for the removal of the lip pinning the zombie, in its own cell first and then in the one it is
     * trying to step into. True when a break was requested, in which case the caller owes it the tick.
     */
    public static boolean clearJam(SmartZombie owner, ServerLevel level, WorldAiContext ctx,
                                   int bx, int bz, int sdx, int sdz) {
        if (!CombatMoveConfig.blockOpsEnabled) {
            return false;
        }
        Zombie entity = owner.entity();
        int y = entity.blockPosition().getY();
        double body = entity.getBbHeight();
        // Own cell first: a zombie already lifted onto the carpet has its head in the lid and cannot walk off
        // the cell in any direction, so nothing ahead of it matters until this one is clear.
        return request(owner, level, ctx, new BlockPos(bx, y, bz), body)
                || ((sdx != 0 || sdz != 0)
                    && request(owner, level, ctx, new BlockPos(bx + sdx, y, bz + sdz), body));
    }

    private static boolean request(SmartZombie owner, ServerLevel level, WorldAiContext ctx,
                                   BlockPos pos, double body) {
        BlockState s = level.getBlockState(pos);
        if (s.blocksMotion()) {
            return false; // a wall rather than a lip, and Obstacle already owns that case
        }
        VoxelShape shape = s.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        double lip = shape.max(Direction.Axis.Y);
        double room = ceilingAbove(level, pos) - pos.getY();
        // Only when the lip is the whole difference. A gap too low even bare is a ceiling problem, and taking
        // the carpet out of one would cost the player a block and change nothing.
        if (lip <= 0.0 || room < body || room - lip >= body) {
            return false;
        }
        if (!MaterialRegistry.isBreakable(level, pos, s)) {
            return false;
        }
        ctx.breakManager().request(pos, owner.entity());
        owner.setState(ZombieState.BREAKING);
        return true;
    }

    /** Y of the first motion-blocking level above {@code pos}, or one past the scan when the column stays open. */
    private static int ceilingAbove(ServerLevel level, BlockPos pos) {
        for (int i = 1; i <= CEILING_SCAN; i++) {
            if (level.getBlockState(pos.above(i)).blocksMotion()) {
                return pos.getY() + i;
            }
        }
        return pos.getY() + CEILING_SCAN + 1;
    }
}
