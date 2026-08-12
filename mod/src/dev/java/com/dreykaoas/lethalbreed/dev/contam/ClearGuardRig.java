package com.dreykaoas.lethalbreed.dev.contam;

import com.dreykaoas.lethalbreed.dev.command.LethalDevCommand;

import com.dreykaoas.lethalbreed.effect.ContaminationManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;

/**
 * The victims and the two cure routes used by the clear-guard verification, kept apart from the checks that
 * read them.
 *
 * <p><b>Why {@link #milk} and {@link #commandClear} are the real thing, not a mock.</b> Vanilla's
 * {@link ClearAllStatusEffectsConsumeEffect#apply} is literally {@code return livingEntity.removeAllEffects();}
 * — verified against this project's 1.21.11 mappings: the class is a record, {@code INSTANCE} is
 * {@code public static final}, and {@code apply(Level, ItemStack, LivingEntity)} is public. So:
 * <ul>
 *   <li>{@link #milk} = the byte-for-byte call a player drinking milk makes, and the only method
 *       {@code MilkKeepsPlagueMixin} redirects inside.</li>
 *   <li>{@link #commandClear} = exactly what {@code EffectCommands.clearEffects} calls.</li>
 * </ul>
 * Nothing here simulates the distinction; both routes bottom out in the shipped mixins.
 *
 * <p><b>Why not {@code /lethaldev}.</b> {@code LethalDevCommand.target()} calls
 * {@code getSource().getPlayerOrException()}, which throws for the console source a headless run has, so the
 * rig drives {@link ContaminationManager} directly.
 */
public final class ClearGuardRig {
    private ClearGuardRig() {}

    /** A row of {@code count} cows marching north from ({@code cx}, {@code cz}), {@code spacing} apart. */
    public static Cow[] cowRow(ServerLevel ow, int cx, int cz, int spacing, int count) {
        Cow[] cows = new Cow[count];
        for (int i = 0; i < count; i++) {
            cows[i] = ContamRig.cow(ow, cx, cz + i * spacing, 0.0f);
        }
        return cows;
    }

    public static void infect(Cow c, boolean symptomatic) {
        if (c == null) {
            return;
        }
        ContaminationManager.contaminate(c);
        if (symptomatic) {
            DevContam.forceSymptomatic(c);
        }
    }

    /** Drink milk at {@code c} — the mixin-redirected path that must KEEP the plague. */
    public static void milk(ServerLevel ow, Cow c) {
        if (c != null) {
            ClearAllStatusEffectsConsumeEffect.INSTANCE.apply(ow, new ItemStack(Items.MILK_BUCKET), c);
        }
    }

    /** {@code /effect clear} at {@code c} — the path that must CURE the plague. Returns false if there was
     *  no cow to clear, so a caller can tell "ran and cured" from "never ran". */
    public static boolean commandClear(Cow c) {
        if (c == null) {
            return false;
        }
        c.removeAllEffects();
        return true;
    }

    public static void discard(Cow[] cows) {
        for (Cow c : cows) {
            if (c != null) {
                c.discard();
            }
        }
    }
}
