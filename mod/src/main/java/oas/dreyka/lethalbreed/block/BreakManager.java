package oas.dreyka.lethalbreed.block;

import oas.dreyka.lethalbreed.config.domain.CombatMoveConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Progressive, player-like block breaking. Zombies <i>request</i> a block each tick they want it gone;
 * the manager accumulates damage over time (scaled by hardness), shows the vanilla cracking overlay
 * via {@link ServerLevel#destroyBlockProgress}, and finally destroys it (with drops). A request not
 * renewed within a grace window is abandoned and its cracks clear, so a zombie that walks away or
 * whose block changes doesn't leave a half-broken ghost.
 */
public final class BreakManager {
    private static final class State extends CrackingBlock {
        float progress;
        long lastRequest;
        LivingEntity breaker;
        /** Breaker entity id -> tick it last requested this block. Distinct ids within the grace window are
         *  the "concentration" count: more zombies on the same block break it faster. */
        final Map<Integer, Long> requesters = new HashMap<>();
    }

    private final Map<Long, State> active = new HashMap<>();
    private long now = 0L;
    private int breakerSeq = 100_000; // synthetic ids so multiple cracks can show at once

    /** Mark a block as being broken by {@code breaker} (call every tick the zombie wants it). */
    public void request(BlockPos pos, LivingEntity breaker) {
        if (!CombatMoveConfig.blockOpsEnabled) {
            return; // master toggle: no breaking at all
        }
        long key = pos.asLong();
        // Anti-TPS cap: a brand-new break is ignored once we're already breaking the max distinct blocks.
        if (!active.containsKey(key) && active.size() >= CombatMoveConfig.maxConcurrentBreaks) {
            return;
        }
        // get-then-put rather than computeIfAbsent: the lambda captures this (it reads breakerSeq) and would
        // be allocated on every request, which is once per breaking zombie per activation.
        State s = active.get(key);
        if (s == null) {
            s = new State();
            s.breakerId = breakerSeq++;
            active.put(key, s);
        }
        s.lastRequest = now;
        s.breaker = breaker;
        if (breaker != null) {
            s.requesters.put(breaker.getId(), now); // count this zombie toward the block's concentration
        }
    }

    /** Advance all active breaks. Called once per tick per dimension on the server thread. */
    public void tick(ServerLevel level, long tick) {
        now = tick;
        if (active.isEmpty()) {
            return;
        }
        float rate = CombatMoveConfig.breakProgressPerTick;
        long grace = CombatMoveConfig.breakGraceTicks;

        Iterator<Map.Entry<Long, State>> it = active.entrySet().iterator();
        // One cursor for the whole sweep, the same shape PlacedBlockTracker uses and for the same reason:
        // a BlockPos per entry per tick, with maxConcurrentBreaks of them per dimension. Every call below
        // reads its coordinates on the spot, and the two that keep a position (the crack packets) freeze it
        // themselves inside CrackingBlock.
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (it.hasNext()) {
            Map.Entry<Long, State> e = it.next();
            State s = e.getValue();
            pos.set(e.getKey());

            if (now - s.lastRequest > grace) {
                s.clearCracks(level, pos); // request went stale
                it.remove();
                continue;
            }
            // Never force a chunk load from here, same guard PlacedBlockTracker uses: getBlockState()
            // resolves through getChunk(..., requireChunk = true), which on a cache miss stalls the server
            // thread on addTicket + managedBlock + join. An unloaded break is held rather than dropped,
            // because the grace check above already retires it if the chunk never comes back.
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState bs = level.getBlockState(pos);
            if (!MaterialRegistry.isBreakable(level, pos, bs)) {
                s.clearCracks(level, pos);
                it.remove();
                continue;
            }
            float hardness = Math.max(0.1f, bs.getDestroySpeed(level, pos));
            // A held tool (pickaxe, etc.) mines faster, like a player. Bare hand / wrong tool = 1.0.
            float toolSpeed = 1.0f;
            if (s.breaker != null && s.breaker.isAlive()) {
                float ds = s.breaker.getMainHandItem().getDestroySpeed(bs);
                if (ds > 1.0f) {
                    toolSpeed = ds;
                }
            }
            // Concentration bonus: count distinct zombies that requested this block within the grace window;
            // more breakers → faster, capped so a huge pile can't shred instantly.
            s.requesters.values().removeIf(rt -> now - rt > grace);
            int concentrators = Math.max(1, s.requesters.size());
            float mult = (float) Math.min(CombatMoveConfig.breakConcentrationCap,
                    1.0 + (concentrators - 1) * CombatMoveConfig.breakConcentrationPerBreaker);
            s.progress += rate * toolSpeed * mult / hardness;
            s.showStage(level, pos, PlacedBlockPolicy.stage(s.progress * 10f));
            if (s.progress >= 1.0f) {
                level.destroyBlock(pos, CombatMoveConfig.breakDropsItems, null, 512);
                s.clearCracks(level, pos);
                it.remove();
            }
        }
    }

}
