package com.dreykaoas.lethalbreed.dev.mechanics;

import com.dreykaoas.lethalbreed.dev.arena.ArenaBuilder;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.dev.config.ConfigOverride;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;

import static com.dreykaoas.lethalbreed.dev.mechanics.MechTestState.Y;

/**
 * The daylight-burn area: open sky, an idle husk and an idle zombie, both of which must catch fire.
 *
 * <p><b>This check could not pass, for three independent reasons, and all three are set up here.</b> It shares
 * one persistent world and one process with the other mechanics areas, and every one of them used to be built
 * in the same tick:
 *
 * <ul>
 *   <li><b>The phase.</b> The gear area sets phase 15 to get scaled stats. {@code SmartZombie.applySunBurn}
 *       returns immediately once {@code PhaseManager.current() >= sunImmunePhase} (5) — the horde stops
 *       burning as it toughens up, by design. Building both areas together therefore disabled sun-burn before
 *       the burn window even opened. The two now run as separate STAGES: this one at a phase below the
 *       immunity, the other after it has been judged.</li>
 *   <li><b>The husk.</b> {@code SpawnFilter} culls every MONSTER that is not exactly {@code Zombie.class}
 *       while {@code onlyPlainZombie} is on, so the husk was discarded at {@code ENTITY_LOAD} — the old
 *       failure line said {@code removed=true} and nobody read it. The filter is lifted for this stage.</li>
 *   <li><b>The weather.</b> {@code applySunBurn} returns early on {@code isInWaterOrRain}, and the shade rig
 *       deliberately sets 24 000 ticks of rain in this same shared world. A shade run that was killed before
 *       its teardown leaves the save raining, and the sun-burn area then silently measures nothing. The sky is
 *       now pinned clear here rather than inherited.</li>
 * </ul>
 *
 * <p>Props are built with {@code create()} + {@code addFreshEntity} rather than {@code EntityType.spawn}
 * because vanilla {@code finalizeSpawn} rolls a baby about 5% of the time and {@code blockBabyZombies} then
 * discards it — a prop that vanishes before it can burn is a flaky false FAIL about something else entirely.
 * {@code addFreshEntity} still fires {@code ENTITY_LOAD}, so the props are registered either way.
 */
public final class MechSunArena {
    private MechSunArena() {}

    static final int CX = 30;

    public static void build(ServerLevel ow, MechTestState s, ConfigOverride cfg) {
        cfg.set("onlyPlainZombie", false);       // or the husk is culled at ENTITY_LOAD
        cfg.set("forceAllZombiesSunBurn", true); // the mechanic under test
        cfg.set("blockBabyZombies", true);       // unchanged, stated so the create() rationale above holds
        ow.setWeatherParameters(24000, 0, false, false); // clear: rain suppresses the burn entirely

        ArenaBuilder.forceChunks(ow, CX);
        MechTestArena.floor(ow, CX, false);
        // Guarantee OPEN sky over the props. The dev tests share one persistent world, and another test's
        // arena can sit at these coordinates (the special-test platform's case #0 is also at x=30 and carries
        // a glowstone roof at Y+4) — a leftover roof would block canSeeSky and silently kill the sun-burn.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -4; dz <= 6; dz++) {
                for (int dy = 0; dy <= 8; dy++) {
                    ow.setBlock(new BlockPos(CX + dx, Y + dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        s.husk = EntityType.HUSK.create(ow, EntitySpawnReason.COMMAND);
        if (s.husk != null) {
            s.husk.setPos(30.5, Y, 0.5);
            s.husk.setPersistenceRequired();
            s.husk.setNoAi(true); // stay on the open platform (don't wander into shade/void)
            ow.addFreshEntity(s.husk);
        }
        s.sunZombie = EntityType.ZOMBIE.create(ow, EntitySpawnReason.COMMAND);
        if (s.sunZombie != null) {
            s.sunZombie.setPos(32.5, Y, 0.5);
            s.sunZombie.setPersistenceRequired();
            s.sunZombie.setNoAi(true);
            ow.addFreshEntity(s.sunZombie);
        }
        LethalBreed.LOGGER.info("[MechTest] sun arena built: husk={} zombie={} raining={} bright={}",
                s.husk != null, s.sunZombie != null, ow.isRaining(), ow.isBrightOutside());
    }

    /**
     * The burn verdict. Reads the LATCHED flag: a prop can ignite and burn to death inside the window, and the
     * instantaneous fire state at the evaluation tick would then read as "never burned".
     *
     * <p>Both props are also asserted still present. {@code removed=true} was the visible symptom of the husk
     * being culled, and a removed prop makes the burn question unanswerable rather than answered "no".
     */
    public static void evaluate(ServerLevel ow, MechTestState s, Check check) {
        boolean huskBurn = s.husk != null && (s.huskWasOnFire || s.husk.getRemainingFireTicks() > 0);
        boolean zBurn = s.sunZombie != null && (s.sunZombieWasOnFire || s.sunZombie.getRemainingFireTicks() > 0);

        // A prop that is GONE is only a problem if it never caught fire. Burning to death is the successful
        // outcome of a burn test, and the first version of this check called that a failure — the zombie
        // ignited, died of it, and was reported as culled. What must never happen is a prop vanishing without
        // ever igniting: that is the SpawnFilter cull, and it makes the burn question unanswerable rather than
        // answered "no".
        boolean huskOk = s.husk != null && (huskBurn || !s.husk.isRemoved());
        boolean zOk = s.sunZombie != null && (zBurn || !s.sunZombie.isRemoved());
        check.record("sun-props-not-culled", huskOk && zOk,
                "husk ignited=" + huskBurn + " present=" + (s.husk != null && !s.husk.isRemoved())
                        + " | zombie ignited=" + zBurn + " present="
                        + (s.sunZombie != null && !s.sunZombie.isRemoved())
                        + " — a prop that disappeared having never caught fire was culled at ENTITY_LOAD");

        boolean sky = s.husk != null && ow.canSeeSky(s.husk.blockPosition());
        check.record("sunburn", huskBurn && zBurn,
                "husk=" + huskBurn + " zombie=" + zBurn + " | bright=" + ow.isBrightOutside()
                        + " raining=" + ow.isRaining() + " skyAtHusk=" + sky
                        + " huskPos=" + (s.husk == null ? "null" : s.husk.blockPosition().toString()));

        if (s.husk != null) {
            s.husk.discard();
        }
        if (s.sunZombie != null) {
            s.sunZombie.discard();
        }
        ArenaBuilder.releaseChunks(ow, CX, 0);
    }

    /** How this area reports, so the arena code does not need a handle on the harness instance. */
    @FunctionalInterface
    public interface Check {
        void record(String name, boolean pass, String detail);
    }
}
