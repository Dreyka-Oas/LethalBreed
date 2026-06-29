package com.dreykaoas.lethalbreed.block;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;

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
 * renewed within a grace window is abandoned and its cracks clear — so a zombie that walks away or
 * whose block changes doesn't leave a half-broken ghost.
 */
public final class BreakManager {
    private static final class State {
        int breakerId;
        float progress;
        long lastRequest;
        int lastStage = -1;
        LivingEntity breaker;
    }

    private final Map<Long, State> active = new HashMap<>();
    private long now = 0L;
    private int breakerSeq = 100_000; // synthetic ids so multiple cracks can show at once

    /** Mark a block as being broken by {@code breaker} (call every tick the zombie wants it). */
    public void request(BlockPos pos, LivingEntity breaker) {
        State s = active.computeIfAbsent(pos.asLong(), k -> {
            State ns = new State();
            ns.breakerId = breakerSeq++;
            return ns;
        });
        s.lastRequest = now;
        s.breaker = breaker;
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
        while (it.hasNext()) {
            Map.Entry<Long, State> e = it.next();
            State s = e.getValue();
            BlockPos pos = BlockPos.of(e.getKey());

            if (now - s.lastRequest > grace) {
                level.destroyBlockProgress(s.breakerId, pos, -1); // clear cracks
                it.remove();
                continue;
            }
            BlockState bs = level.getBlockState(pos);
            if (!MaterialRegistry.isBreakable(level, pos, bs)) {
                level.destroyBlockProgress(s.breakerId, pos, -1);
                it.remove();
                continue;
            }
            float hardness = Math.max(0.1f, bs.getDestroySpeed(level, pos));
            // A held tool (pickaxe, etc.) mines faster — like a player. Bare hand / wrong tool = 1.0.
            float toolSpeed = 1.0f;
            if (s.breaker != null && s.breaker.isAlive()) {
                float ds = s.breaker.getMainHandItem().getDestroySpeed(bs);
                if (ds > 1.0f) {
                    toolSpeed = ds;
                }
            }
            s.progress += rate * toolSpeed / hardness;
            int stage = (int) Math.max(0, Math.min(9, s.progress * 10f));
            if (stage != s.lastStage) {
                level.destroyBlockProgress(s.breakerId, pos, stage);
                s.lastStage = stage;
            }
            if (s.progress >= 1.0f) {
                level.destroyBlock(pos, true, null, 512); // drop items + break effects
                level.destroyBlockProgress(s.breakerId, pos, -1);
                it.remove();
            }
        }
    }

    public int activeCount() {
        return active.size();
    }

    public void clear() {
        active.clear();
    }
}
