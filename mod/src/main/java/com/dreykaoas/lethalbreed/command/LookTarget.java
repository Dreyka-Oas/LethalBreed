package com.dreykaoas.lethalbreed.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * "The entity I am looking at, else me" — the target rule shared by every command that acts on a single
 * victim.
 *
 * <p>It sits in the shipped source set because {@code /lethaldev level} does. The remaining
 * {@code /lethaldev} subcommands are dev-only but resolve their target through the same code: a second
 * copy in {@code src/dev} would be the same raycast maintained twice, and a command that picks a
 * different victim in dev than in a player's world is a debugging trap.
 */
public final class LookTarget {
    private LookTarget() {}

    /** Range, in blocks, of the "what am I looking at" raycast. */
    private static final double LOOK_REACH = 24.0;

    /**
     * The living entity the sender is looking at within {@value #LOOK_REACH} blocks, or the sender itself
     * when the crosshair is on nothing.
     *
     * @throws CommandSyntaxException when the sender is not a player — there is no crosshair to read, so a
     *         console sender has no target to fall back to either
     */
    public static LivingEntity of(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(LOOK_REACH));
        AABB box = player.getBoundingBox().expandTowards(look.scale(LOOK_REACH)).inflate(1.0);
        LivingEntity best = null;
        double bestT = Double.MAX_VALUE;
        List<Entity> candidates = player.level().getEntities(player, box,
                e -> e instanceof LivingEntity && e.isAlive() && e.isPickable());
        for (Entity e : candidates) {
            var clip = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (clip.isPresent()) {
                double t = clip.get().distanceToSqr(eye);
                if (t < bestT) {
                    bestT = t;
                    best = (LivingEntity) e;
                }
            }
        }
        return best != null ? best : player;
    }

    /** How to name a target in command feedback: a player by its account name, anything else by its type. */
    public static String name(LivingEntity e) {
        return e instanceof ServerPlayer p ? p.getGameProfile().name() : e.getType().toString();
    }
}
