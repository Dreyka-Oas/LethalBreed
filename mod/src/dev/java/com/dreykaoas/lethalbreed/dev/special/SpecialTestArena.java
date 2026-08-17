package com.dreykaoas.lethalbreed.dev.special;

import com.dreykaoas.lethalbreed.dev.arena.ArenaBuilder;
import com.dreykaoas.lethalbreed.dev.DevVerdict;

import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import com.dreykaoas.lethalbreed.special.SpecialBehavior;
import com.dreykaoas.lethalbreed.special.SpecialRoller;
import com.dreykaoas.lethalbreed.special.SpecialType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.List;

import static com.dreykaoas.lethalbreed.dev.special.SpecialTestCase.SPACING;
import static com.dreykaoas.lethalbreed.dev.special.SpecialTestCase.Y;

/** Builds the sheltered night arena and drops one forced special zombie per type. */
public final class SpecialTestArena {
    private SpecialTestArena() {}

    /** Build the arena and append the created cases to {@code cases}. */
    public static void build(ServerLevel ow, MinecraftServer server, List<SpecialTestCase> cases) {
        server.setDifficulty(Difficulty.HARD, true);
        // Phase 1, not phase 0. This is the only one of the twelve rigs that never set a phase, so it inherited
        // whatever the save happened to carry; on a fresh world that is 0, where SpawnFilter culls every
        // MONSTER at ENTITY_LOAD — the arena then builds ZERO cases and the suite prints "0/0 PASS" plus its
        // ALL DONE marker, a green gate proving nothing. Phase 1 also leaves SpecialType.available(1) empty
        // (the earliest unlock is 2), so no Splitter or Necromancer child can roll a special of its own and
        // muddy the "children are plain" assertions.
        PhaseManager.get().setPhase(server, 1);
        // Silence every other source of the effects the passive checks look for: at phase 14 roughly a third
        // of zombies carry a pool-rolled Speed/Leap/Resistance, so those checks would pass with the special
        // roller deleted outright.
        WorldSpawnConfig.randomEffectEnabled = false;
        SpecialBehavior.HURL_COUNT.set(0);
        SpecialBehavior.HEAL_COUNT.set(0);
        SpecialBehavior.SUMMON_COUNT.set(0);
        ow.setDayTime(18000L);                              // midnight — no sun-burn
        // No natural spawns — else stray monsters give the "lone" test zombies false targets (SPAWN_MOBS was
        // RULE_DOMOBSPAWNING in older mappings).
        ow.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS, false, server);
        ow.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MONSTERS, false, server);
        ContaminationConfig.contaminationEnabled = false;     // keep cows alive for the per-special checks
        TargetingConfig.targetDetectRadius = 10.0;        // tight so a "lone" zombie stays target-less
        // Disable hearing for this arena: a special zombie shoving its invulnerable cow gives the cow a little
        // horizontal velocity, which makes it AUDIBLE — and the SCREAMER's "lone" extra (11 blocks away, inside
        // the 24-block hearing range) would then hear+target the cow on its own, so it is no longer target-less
        // and the howler's rally finds nothing to retarget (a flaky false FAIL). Hearing isn't under test here;
        // every case's cow is within the 10-block sight range with clear LOS, so vision alone drives targeting.
        TargetingConfig.soundEnabled = false;

        SpecialType[] types = {
                SpecialType.SPRINTER, SpecialType.LEAPER, SpecialType.JUGGERNAUT, SpecialType.BOMBER,
                SpecialType.SCREAMER, SpecialType.HEALER, SpecialType.NECROMANCER, SpecialType.SPLITTER,
        };

        for (int i = 0; i < types.length; i++) {
            int cx = i * SPACING + 30;
            BlockPos pos = new BlockPos(cx, Y, 0);
            ArenaBuilder.forceChunks(ow, cx);
            buildPlatform(ow, cx);
            // Sweep the platform before using it. Summoned and split children are setPersistenceRequired and
            // this mod never despawns anything, so each run leaves its offspring standing exactly where the
            // next run needs the space. That debris is not cosmetic: with 37 zombies left over, the
            // Necromancer's density cap refused to summon at all and the check failed on a world state its
            // own previous runs had created.
            for (Zombie stale : ow.getEntitiesOfClass(Zombie.class, new AABB(pos).inflate(24))) {
                stale.discard();
            }

            Zombie z = ArenaBuilder.spawnZombie(ow, pos);
            if (z == null) {
                continue;
            }
            z.setPersistenceRequired();
            SpecialType type = types[i];
            SpecialRoller.assign(z, type);

            Cow cow = spawnCow(ow, cx, type, z);
            Zombie extra = spawnExtra(ow, cx, type);
            // A Healer only heals what is hurt, so the witness has to be hurt for the aura to be observable
            // at all. WOUNDED is far below any max health the phase can produce, and nothing else on this
            // sealed platform can damage it, so any rise above this figure came from the aura.
            if (type == SpecialType.HEALER && extra != null) {
                extra.setHealth(SpecialTestCase.WOUNDED);
            }
            cases.add(new SpecialTestCase(type, z, cow, extra, pos));
        }
        LethalBreed.LOGGER.info("[SpecialTest] arena built — {} cases", cases.size());
        // The suite must fail loudly when it builds nothing, instead of reporting a vacuous 0/0 pass.
        DevVerdict.check(SpecialTestEvaluator.SUITE, "arene-construite", cases.size() == types.length,
                "cases=" + cases.size() + "/" + types.length + " phase=" + PhaseManager.current());
    }

    /** Per-case sheltered platform. GLOWSTONE roof = fully lit → no hostile mobs spawn on it. */
    private static void buildPlatform(ServerLevel ow, int cx) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -4; dz <= 11; dz++) {
                ow.setBlock(new BlockPos(cx + dx, Y - 1, dz), Blocks.GLOWSTONE.defaultBlockState(), 3);
                ow.setBlock(new BlockPos(cx + dx, Y + 4, dz), Blocks.GLOWSTONE.defaultBlockState(), 3);
            }
        }
    }

    private static Cow spawnCow(ServerLevel ow, int cx, SpecialType type, Zombie z) {
        // Cow target position differs per type so the ability's trigger condition can be met.
        int cowZ = type == SpecialType.SCREAMER ? -2 : 2;
        Cow cow = EntityType.COW.spawn(ow, new BlockPos(cx, Y, cowZ), EntitySpawnReason.COMMAND);
        if (cow != null) {
            cow.setNoAi(true);
            cow.setInvulnerable(true);        // survive melee so its applied effects stay observable
            cow.setPersistenceRequired();
        }
        z.setTarget(cow);
        if (type == SpecialType.SCREAMER) {
            // Keep the special anchored: SCREAMER must not dive into
            // the invulnerable cow and pile up — repeatedly bonking it triggers zombie reinforcements that
            // crowd in, shove the "lone" extra and the cow around, and flake the rally check. noAi specials
            // still run their ability each activation (it keeps its cow target by sight at 2 blocks).
            z.setNoAi(true);
        }
        return cow;
    }

    private static Zombie spawnExtra(ServerLevel ow, int cx, SpecialType type) {
        Zombie extra = null;
        if (type == SpecialType.SCREAMER) {
            // 9 blocks from the howler (< its 24 radius) but 11 from the cow (> detect 10) → stays targetless.
            extra = ArenaBuilder.spawnZombie(ow, new BlockPos(cx, Y, 9));
        } else if (type == SpecialType.HEALER) {
            extra = ArenaBuilder.spawnZombie(ow, new BlockPos(cx + 1, Y, 0));
            if (extra != null) {
                extra.setHealth(4.0f); // hurt so Regen is observable
            }
        }
        if (extra != null) {
            extra.setPersistenceRequired();
        }
        return extra;
    }
}
