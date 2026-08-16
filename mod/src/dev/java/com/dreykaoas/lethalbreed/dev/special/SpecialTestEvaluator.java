package com.dreykaoas.lethalbreed.dev.special;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.dev.DevVerdict;
import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;
import com.dreykaoas.lethalbreed.special.SpecialAttachment;
import com.dreykaoas.lethalbreed.special.SpecialBehavior;
import com.dreykaoas.lethalbreed.special.SpecialType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Logs PASS/FAIL per ability and kills splitters mid-run so their DEATH special fires. */
public final class SpecialTestEvaluator {

    /** Suite name, shared with the harness that emits the summary. */
    public static final String SUITE = "special";
    private SpecialTestEvaluator() {}

    /** Latched once the Bombeur's witness has been seen carrying the splatter. */
    private static boolean bombeurSplattered;
    /** Zombie ids present around the Splitter's platform just before it is killed. */
    private static final Set<Integer> BEFORE_SPLIT = new HashSet<>();
    /** Position of the Bombeur on the first tick its fuse is lit. Null until it has armed. */
    private static net.minecraft.world.phys.Vec3 bombeurArmedPos;
    /** Largest distance measured from that position while the fuse is burning. */
    private static double bombeurMaxDrift;

    /**
     * Sampled every tick between build and evaluation.
     *
     * <p>The splatter has to be latched rather than read at the end: its effect durations scale with
     * intensity, and the witness sits far enough out that a short fuse gives it about 139 ticks of Nausea
     * from a blast at tick ~35 — expiring before the tick-200 evaluation about one run in five. Reading the
     * end state measured the fuse roll, not the splatter.
     */
    public static void sample(List<SpecialTestCase> cases) {
        for (SpecialTestCase c : cases) {
            if (c.type() == SpecialType.BOMBEUR && c.cow() != null && c.cow().isAlive()
                    && c.cow().getEffect(MobEffects.NAUSEA) != null
                    && c.cow().getEffect(MobEffects.SLOWNESS) != null) {
                bombeurSplattered = true;
            }
            if (c.type() == SpecialType.BOMBEUR && !c.z().isRemoved()
                    && SpecialBehavior.fuseIsLit(c.z())) {
                if (bombeurArmedPos == null) {
                    bombeurArmedPos = c.z().position();
                } else {
                    bombeurMaxDrift = Math.max(bombeurMaxDrift, bombeurArmedPos.distanceTo(c.z().position()));
                }
            }
        }
    }

    public static void killSplitters(ServerLevel ow, List<SpecialTestCase> cases) {
        for (SpecialTestCase c : cases) {
            if (c.type() == SpecialType.SPLITTER && c.z().isAlive()) {
                // Snapshot first: children are setPersistenceRequired, so every past run's offspring is still
                // standing there. Counting the whole box reported "children=4" for a configured 2, and drifted
                // to 11 over successive runs — it was measuring debris, not splitting.
                for (Zombie prior : ow.getEntitiesOfClass(Zombie.class, new AABB(c.pos()).inflate(8))) {
                    BEFORE_SPLIT.add(prior.getId());
                }
                c.z().hurtServer(ow, c.z().damageSources().magic(), 1000f);
            }
        }
    }

    public static void evaluate(ServerLevel ow, List<SpecialTestCase> cases) {
        for (SpecialTestCase c : cases) {
            Zombie z = c.z();
            boolean pass;
            String detail;
            switch (c.type()) {
                case SPRINTEUR -> { pass = z.getEffect(MobEffects.SPEED) != null; detail = "speed effect"; }
                case BONDISSEUR -> { pass = z.getEffect(LethalBreedEffects.LEAP) != null; detail = "LEAP effect"; }
                case JUGGERNAUT -> { pass = z.getEffect(MobEffects.RESISTANCE) != null; detail = "resistance effect"; }
                case BOMBEUR -> {
                    boolean gone = z.isRemoved();
                    boolean alive = c.cow() != null && c.cow().isAlive();
                    // bombeurSplattered is latched by sample(), not read here: the effect durations scale with
                    // intensity and a short fuse lets them lapse before this runs. A dead witness proves the
                    // blast reached it but says nothing about the wider ring, so it is excused.
                    // 0.25 block margin: collision nudges, not real movement. A Bombeur that resumes running
                    // would drift several blocks over the fuse's 1.5-6s window.
                    boolean immobile = bombeurMaxDrift < 0.25;
                    pass = gone && (!alive || bombeurSplattered) && immobile;
                    detail = "explosé=" + gone + " témoinVivant=" + alive + " éclaboussé=" + bombeurSplattered
                            + " dérive=" + String.format("%.2f", bombeurMaxDrift);
                }
                case HURLEUR -> {
                    pass = SpecialBehavior.HURL_COUNT.get() > 0;
                    boolean hasTgt = z.getTarget() != null;
                    var esz = c.extra() == null ? null : GameState.REGISTRY.get(c.extra().getId());
                    int near = ow.getEntitiesOfClass(Zombie.class, new AABB(z.blockPosition()).inflate(24)).size();
                    detail = "retargets x" + SpecialBehavior.HURL_COUNT.get()
                            + " hurlTgt=" + hasTgt + " extraTgt=" + (esz != null && esz.hasTarget())
                            + " near=" + near;
                }
                case SOIGNEUR -> {
                    // Health, not a counter. The aura applied Regeneration for its whole existence, which
                    // vanilla silently refuses on undead — the old check passed on an ability that healed
                    // nothing, and printed extraRegen=false in its own success line while doing so.
                    float hp = c.extra() == null ? 0f : c.extra().getHealth();
                    pass = c.extra() != null && c.extra().isAlive() && hp > SpecialTestCase.WOUNDED;
                    detail = "heals x" + SpecialBehavior.HEAL_COUNT.get()
                            + " extraHp=" + hp + " (blessé à " + SpecialTestCase.WOUNDED + ")";
                }
                case NECROMANCIEN -> {
                    // The counter increments before anything is placed, so pair it with children that exist.
                    long kids = ow.getEntitiesOfClass(Zombie.class, new AABB(c.pos()).inflate(12)).stream()
                            .filter(k -> k != c.z() && k != c.extra() && !k.isRemoved()).count();
                    pass = SpecialBehavior.SUMMON_COUNT.get() > 0 && kids > 0;
                    detail = "summons x" + SpecialBehavior.SUMMON_COUNT.get() + " vivants=" + kids;
                }
                case SPLITTER -> {
                    List<Zombie> kids = ow.getEntitiesOfClass(Zombie.class, new AABB(c.pos()).inflate(8)).stream()
                            .filter(k -> !BEFORE_SPLIT.contains(k.getId()) && !k.isRemoved())
                            .toList();
                    int want = SpecialVariantConfig.specialSplitterChildren;
                    // Plain children: assign(NONE) used to leave the passives a child had already rolled for
                    // itself, so a "small" child could keep Resistance, double health and a spc_scale that
                    // exactly cancelled its shrink.
                    boolean plain = kids.stream().allMatch(k ->
                            SpecialType.fromId(k.getAttached(SpecialAttachment.SPECIAL)) == SpecialType.NONE
                                    && !k.hasCustomName());
                    pass = kids.size() == want && plain;
                    detail = "nouveaux=" + kids.size() + "/" + want + " sansSpecial=" + plain;
                }
                default -> { pass = false; detail = "n/a"; }
            }
            // Through DevVerdict, not a bare log line. This suite spoke its own [SpecialTest] dialect and
            // never emitted ALL DONE, so — exactly like the mechanics suite — LB_DEV_TEST=special was a listed
            // suite the gate could never pass, with six checks running and nobody counting them.
            DevVerdict.check(SUITE, c.type().id(), pass, detail);
        }
    }
}
