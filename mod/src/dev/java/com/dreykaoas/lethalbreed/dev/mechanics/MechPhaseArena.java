package com.dreykaoas.lethalbreed.dev.mechanics;

import com.dreykaoas.lethalbreed.dev.arena.ArenaBuilder;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.dev.config.ConfigOverride;
import com.dreykaoas.lethalbreed.dev.probe.DevSink;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import com.dreykaoas.lethalbreed.probe.DevProbe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

import static com.dreykaoas.lethalbreed.dev.mechanics.MechTestState.Y;

/**
 * The three areas that need a HIGH phase, built after the sun-burn stage has been judged: phase-scaled stats,
 * the contamination DoT, and the flee + distress-rally scenario. All roofed, so nothing here burns.
 *
 * <p>The ten {@code ContaminationConfig} fields this needs used to be written straight onto the holders with
 * no restore at all — the exact leak {@link ConfigOverride} exists to close. They now go through the scope the
 * harness opens for this stage.
 */
public final class MechPhaseArena {
    private MechPhaseArena() {}

    private static final int GEAR_X = 90;
    private static final int CONTAM_X = 150;
    private static final int RALLY_X = 210;
    private static final int GEAR_PHASE = 15;

    public static void build(ServerLevel ow, MinecraftServer server, MechTestState s, ConfigOverride cfg) {
        PhaseManager.get().setPhase(server, GEAR_PHASE);
        // Zero every dev counter up front, once, rather than per-counter at each sub-arena: nothing below has
        // run yet, so this is equivalent to the old per-field .set(0) calls but doesn't need a narrower
        // "reset just this one counter" method on DevSink. Safe unguarded: this class only ever runs after
        // DevBootstrap#install has installed the sink.
        DevSink.get().resetCounters();
        buildPhaseGear(ow, s);
        buildContamination(ow, s, cfg);
        buildFleeRally(ow, s, cfg);
        LethalBreed.LOGGER.info("[MechTest] phase-{} arena built", GEAR_PHASE);
    }

    /** Phase 15 → scaled, varied health. Zombies carry no gear, so there is nothing armour-related to check. */
    private static void buildPhaseGear(ServerLevel ow, MechTestState s) {
        ArenaBuilder.forceChunks(ow, GEAR_X);
        MechTestArena.floor(ow, GEAR_X, true);
        s.gearPos = new BlockPos(GEAR_X, Y, 0);
        for (int i = 0; i < 20; i++) {
            Zombie z = ArenaBuilder.spawnZombie(ow, new BlockPos(GEAR_X - 2 + i % 4, Y, i % 5));
            if (z != null) {
                z.setPersistenceRequired();
            }
        }
    }

    /** A vulnerable cow, infected directly so the measurement is the ramping DoT and not the on-hit spread. */
    private static void buildContamination(ServerLevel ow, MechTestState s, ConfigOverride cfg) {
        cfg.set("contaminationEnabled", true)
                .set("contamBaseChance", 1.0)        // infect on the first hit
                .set("contamDamageMin", 5.0)         // kill the cow well within the window
                .set("contamDamageMax", 5.0)
                .set("contamIntervalMinSec", 0.5)
                .set("contamIntervalMaxSec", 0.5)
                .set("contamSymptomMinDays", 0.0)
                .set("contamSymptomMaxDays", 0.0)
                .set("contamSymptomMinPct", 100.0)
                .set("contamSymptomMaxPct", 100.0);
        ArenaBuilder.forceChunks(ow, CONTAM_X);
        MechTestArena.floor(ow, CONTAM_X, true);
        s.contamPos = new BlockPos(CONTAM_X, Y, 0);
        Cow cow = EntityType.COW.spawn(ow, new BlockPos(CONTAM_X, Y, 1), EntitySpawnReason.COMMAND);
        if (cow != null) {
            cow.setNoAi(true);
            cow.setPersistenceRequired();
            ContaminationManager.contaminate(cow);
        }
    }

    /**
     * A wounded zombie with a live threat 15 blocks away must drop its hunt, retreat, and — once at least
     * {@code distressDistance} from the threat — scream for help; idle helpers within the rally radius must
     * then pick up sound-memory of its spot.
     *
     * <p>The threat is a silent NoAi cow, so the distress emit should be the only sound event in the world.
     * That claim is now ASSERTED rather than assumed: a previous run reported helpers rallied while zero
     * screams had fired, which means the memory came from somewhere else and the check's premise was false.
     */
    private static void buildFleeRally(ServerLevel ow, MechTestState s, ConfigOverride cfg) {
        // Pin every option this scenario depends on, exactly as buildContamination does for its own.
        //
        // fleeEnabled is the one that mattered: the run config ships it FALSE, and ZombieMood reads
        //     fleeThreat = fleeEnabled ? flightThreat(...) : null
        // so the zombie never entered FLEEING and the distress scream could never fire. The check was not
        // flaky on this at all — it was deterministically impossible, and "0 screams while helpers rallied"
        // was the arena telling us so. An arena that leaves a switch it needs to the ambient config is
        // testing the config, not the code.
        cfg.set("fleeEnabled", true)
                .set("moodEnabled", true)
                .set("fleeHealthFraction", 0.3333)   // 4/20 hp = 20 %, comfortably under the threshold
                .set("distressDistance", 12.0)       // the fleer starts 15 blocks out, so it is already past
                .set("distressRallyRadius", 32.0);   // helpers sit ~3 blocks away, well inside
        ArenaBuilder.forceChunks(ow, RALLY_X);
        MechTestArena.floor(ow, RALLY_X, true);
        for (int x = RALLY_X - 3; x <= RALLY_X + 19; x++) {
            for (int dz = -4; dz <= 6; dz++) {
                ow.setBlock(new BlockPos(x, Y - 1, dz), Blocks.STONE.defaultBlockState(), 3);
                ow.setBlock(new BlockPos(x, Y + 4, dz), Blocks.GLOWSTONE.defaultBlockState(), 3);
            }
        }
        Cow threat = EntityType.COW.create(ow, EntitySpawnReason.COMMAND);
        if (threat != null) {
            threat.setPos(RALLY_X + 0.5, Y, 0.5);
            threat.setNoAi(true);
            threat.setPersistenceRequired();
            ow.addFreshEntity(threat);
        }
        Zombie fleer = EntityType.ZOMBIE.create(ow, EntitySpawnReason.COMMAND);
        if (fleer != null) {
            fleer.setPos(RALLY_X + 15.5, Y, 0.5);
            fleer.setPersistenceRequired();
            fleer.setHealth(4.0f); // 20% < fleeHealthFraction → enters FLEEING as soon as a threat is in range
            if (threat != null) {
                fleer.setLastHurtByMob(threat);
            }
            ow.addFreshEntity(fleer);
        }
        s.fleer = fleer;
        s.fleeThreat = threat;

        List<Zombie> helpers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Zombie h = ArenaBuilder.spawnZombie(ow, new BlockPos(RALLY_X + 13 + i % 2, Y, 3 + i));
            if (h != null) {
                h.setPersistenceRequired();
                helpers.add(h);
            }
        }
        s.rallyHelpers = helpers;
    }

    public static void evaluate(ServerLevel ow, MechTestState s, MechSunArena.Check check) {
        evaluateScaling(ow, s, check);
        evaluateContamination(check);
        evaluateRally(s, check);
        for (int cx : new int[] { GEAR_X, CONTAM_X, RALLY_X }) {
            ArenaBuilder.releaseChunks(ow, cx, 0);
        }
    }

    private static void evaluateScaling(ServerLevel ow, MechTestState s, MechSunArena.Check check) {
        int tanky = 0;
        double minHp = Double.MAX_VALUE;
        double maxHp = 0;
        for (Zombie z : ow.getEntitiesOfClass(Zombie.class, new AABB(s.gearPos).inflate(12))) {
            double hp = z.getAttributeValue(Attributes.MAX_HEALTH);
            if (hp > 25.0) {
                tanky++;
            }
            minHp = Math.min(minHp, hp);
            maxHp = Math.max(maxHp, hp);
        }
        check.record("phasescale", tanky > 0 && maxHp > minHp,
                "tanky=" + tanky + " hp=" + String.format("%.1f", minHp) + "–" + String.format("%.1f", maxHp)
                        + " at phase " + PhaseManager.current());
    }

    private static void evaluateContamination(MechSunArena.Check check) {
        DevSink sink = DevSink.get();
        long infect = sink.counter(DevProbe.INFECT);
        long died = sink.counter(DevProbe.DEATH);
        check.record("contamination", infect > 0 && died > 0, "infect=" + infect + " died=" + died);
    }

    private static void evaluateRally(MechTestState s, MechSunArena.Check check) {
        long distress = DevSink.get().counter(DevProbe.DISTRESS);
        boolean fleerAlive = s.fleer != null && !s.fleer.isRemoved();
        check.record("flee-rally", distress > 0 && s.rallyHelped,
                "distressScreams=" + distress + " helpersRallied=" + s.rallyHelped
                        + " fleerPresent=" + fleerAlive
                        + " fleerHealth=" + (fleerAlive ? s.fleer.getHealth() : -1.0f)
                        + " — helpers rallying with zero screams would mean the memory came from some other "
                        + "sound, i.e. the arena is not as silent as this check assumes");
    }
}
