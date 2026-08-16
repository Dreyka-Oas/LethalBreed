package com.dreykaoas.lethalbreed.special.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The gore a Bombeur leaves on the ground: a shrinking puddle that keeps dosing whoever stands in it after
 * the blast is long over. Fleeing the explosion no longer settles it — the ground itself stays hostile until
 * the residue drains.
 *
 * <p><b>Why this is not an {@code AreaEffectCloud}.</b> Vanilla's lingering cloud gates its victims on
 * {@code isAffectedByPotions()} alone, with no way to spare an ally. A Bombeur bursting inside its own pack
 * would blanket that pack in Slowness — the variant would sabotage the horde it fights for. The cloud also
 * only knows how to apply {@code MobEffectInstance}s, so it could never carry this mod's contamination, and
 * it applies one flat dose regardless of where in the radius you stand, discarding the distance falloff that
 * is the whole point of {@link BombeurBlast#intensity}. Ticking the puddle here reuses the burst's own victim
 * filter, its own falloff curve and its own gore application, so the puddle and the blast can never drift
 * apart.
 *
 * <p>State is deliberately transient. A puddle lives seconds, and persisting it would mean an attachment,
 * a codec and a reload path for something that has always expired before any realistic save. It is dropped
 * wholesale at {@code SERVER_STOPPED} so a closed world's {@link ServerLevel} is not pinned into the next
 * session, the same contract {@code ContaminationManager.onServerStopped} honours.
 */
public final class GorePuddles {
    private GorePuddles() {}

    /**
     * One puddle. {@code ratio} is the fuse ratio of the Bombeur that left it, carried so the dose keeps
     * scaling with how long that zombie swelled — a puddle from a long fuse stays nastier, not just wider.
     */
    private static final class Puddle {
        final ServerLevel level;
        final double x, y, z;
        final double radius0;
        final double ratio;
        final int durationTicks;
        int age;

        Puddle(ServerLevel level, double x, double y, double z, double radius0, double ratio, int durationTicks) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius0 = radius0;
            this.ratio = ratio;
            this.durationTicks = durationTicks;
        }
    }

    private static final List<Puddle> ACTIVE = new ArrayList<>();

    /** Emit the puddle's particles this often. Every tick would be a needless packet storm for a haze. */
    private static final int PARTICLE_INTERVAL_TICKS = 5;

    /**
     * Leave a puddle where a Bombeur burst. No-op for a degenerate radius or duration, so a server that has
     * configured the splatter away does not accumulate invisible zero-size entries.
     */
    public static void spawn(ServerLevel level, double x, double y, double z, double ratio, double splatterRadius) {
        double radius0 = BombeurBlast.puddleRadius(splatterRadius);
        int duration = BombeurBlast.puddleDurationTicks(ratio);
        if (radius0 <= 0.0 || duration <= 0) {
            return;
        }
        ACTIVE.add(new Puddle(level, x, y, z, radius0, ratio, duration));
    }

    /** END_SERVER_TICK: age every puddle, dose whoever is standing in one, and drop the expired. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        ACTIVE.removeIf(p -> {
            // Age first: a puddle created this tick should not immediately dose at full strength AND render,
            // and shrinking from age 0 keeps puddleRadiusAt's "reaches 0 exactly at expiry" contract honest.
            p.age++;
            if (p.age >= p.durationTicks) {
                return true;
            }
            double radius = BombeurBlast.puddleRadiusAt(p.radius0, p.age, p.durationTicks);
            if (radius <= 0.0) {
                return true;
            }
            if (p.age % PARTICLE_INTERVAL_TICKS == 0) {
                SpecialAbilities.gorePuddleParticles(p.level, p.x, p.y, p.z, radius);
            }
            if (p.age % BombeurBlast.PUDDLE_REAPPLY_TICKS == 0) {
                dose(p, radius);
            }
            return false;
        });
    }

    /** Apply one round of residue to everyone inside the puddle right now. */
    private static void dose(Puddle p, double radius) {
        for (LivingEntity victim : SpecialAbilities.splatterVictims(p.level, p.x, p.y, p.z, radius, null)) {
            double dist = Math.sqrt(victim.distanceToSqr(p.x, p.y, p.z));
            double intensity = BombeurBlast.puddleIntensity(p.ratio, dist, radius);
            if (intensity > 0.0) {
                // Effects only, no contamination roll. The puddle re-doses every PUDDLE_REAPPLY_TICKS, so
                // rolling infection each round would make standing in one for a few seconds a near-certain
                // infection — infection stays the explosion's signature, the puddle stays lingering poison.
                SpecialAbilities.applyGore(victim, intensity);
            }
        }
    }

    /** SERVER_STOPPED: drop every puddle so the closed world's level is not held into the next session. */
    public static void onServerStopped() {
        ACTIVE.clear();
    }
}
