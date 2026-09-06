package oas.dreyka.lethalbreed.special;

import oas.dreyka.lethalbreed.LethalBreed;
import oas.dreyka.lethalbreed.api.variant.SpecialVariant;
import oas.dreyka.lethalbreed.api.variant.SpecialVariantRegistry;
import oas.dreyka.lethalbreed.config.domain.SpecialVariantConfig;
import oas.dreyka.lethalbreed.dimension.WorldAiContext;
import oas.dreyka.lethalbreed.entity.SmartZombie;
import oas.dreyka.lethalbreed.special.runtime.BomberBlast;
import oas.dreyka.lethalbreed.special.runtime.SpecialAbilities;
import oas.dreyka.lethalbreed.special.runtime.VariantTickContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Runtime behaviour for ACTIVE special zombies (per-activation, cooldown-gated) and DEATH specials. */
public final class SpecialBehavior {
    private SpecialBehavior() {}

    // Dev instrumentation (headless test harness reads these to confirm abilities fired).
    public static final AtomicInteger SUMMON_COUNT = new AtomicInteger();
    public static final AtomicInteger HURL_COUNT = new AtomicInteger();
    public static final AtomicInteger HEAL_COUNT = new AtomicInteger();

    /**
     * True while a Bomber's fuse is burning: it has armed and is committed to detonating.
     *
     * <p>Read by {@code LodManager} to refuse FROZEN for such a zombie. {@code LodBucketPass} skips a FROZEN
     * zombie BEFORE it ever reaches {@code tick()}, so freezing a lit Bomber stops its fuse mid-burn: the
     * belly stops swelling client-side and it becomes a dormant mine that detonates the instant a player
     * wanders back into range, however many minutes later. The deadline is absolute, so the fix is simply to
     * keep the zombie ticking until it goes off.
     */
    public static boolean fuseIsLit(Zombie z) {
        return z.getAttachedOrElse(SpecialAttachment.BOMBER_FUSE, 0) > 0;
    }

    /** Called every activation from {@code SmartZombie.tick}, whoever the variant belongs to. */
    public static void tick(SmartZombie sz, ServerLevel level, WorldAiContext ctx) {
        SpecialVariant v = sz.pursuit().variant();
        if (v == null || v.kind() != SpecialVariant.Kind.ACTIVE) {
            return;
        }
        run(v, () -> v.behavior().tick(new VariantTickContext(sz, level, ctx)));
    }

    /**
     * Call a variant and survive it going wrong.
     *
     * <p>Reported once per variant and then swallowed: this runs on every activation of every zombie
     * carrying it, so a variant that throws reliably would otherwise write the same stack trace a few
     * hundred times a second and bury everything else in the log.
     */
    private static void run(SpecialVariant v, Runnable call) {
        try {
            call.run();
        } catch (Throwable t) {
            if (BLAMED.add(v.id())) {
                LethalBreed.LOGGER.error("[LethalBreed] variant {} threw, and is being reported once only",
                        v.id(), t);
            }
        }
    }

    private static final Set<String> BLAMED = ConcurrentHashMap.newKeySet();

    /** The eight shipped cases, exactly as they were; each self-gates on target + cooldown. */
    static void shippedTick(SpecialType t, SmartZombie sz, ServerLevel level, WorldAiContext ctx) {
        Zombie z = sz.entity();
        LivingEntity tgt = z.getTarget();
        if (tgt == null) {
            tgt = sz.targetEntity(); // fall back to our own target (vanilla getTarget is set later in the tick)
        }
        switch (t) {
            case BOMBER -> {
                // Absolute deadline, not per-activation accumulation: this method only runs once every
                // `tickBuckets` ticks, so counting activations tied a gameplay tempo to a performance knob.
                // Raising tickBuckets silently doubled the time before detonation.
                int fuse = z.getAttachedOrElse(SpecialAttachment.BOMBER_FUSE, 0);
                long now = level.getGameTime();
                if (fuse <= 0) {
                    double armRange = SpecialVariantConfig.specialBomberArmRange;
                    boolean inRange = tgt != null && z.distanceToSqr(tgt) <= armRange * armRange;
                    if (!inRange) {
                        break;
                    }
                    fuse = BomberBlast.fuseTicksFor(z.getRandom().nextDouble());
                    z.setAttached(SpecialAttachment.BOMBER_FUSE, fuse);
                    z.setAttached(SpecialAttachment.BOMBER_ARMED_AT, now);
                    if (LethalBreed.LOGGER.isDebugEnabled()) {
                        LethalBreed.LOGGER.debug("[LethalBreed] Bomber armed at {} ({} blocks away, fuse={} ticks)",
                                tgt instanceof Player pl ? pl.getName().getString()
                                        : tgt.getClass().getSimpleName(),
                                Math.sqrt(z.distanceToSqr(tgt)), fuse);
                    }
                }
                long elapsed = now - z.getAttachedOrElse(SpecialAttachment.BOMBER_ARMED_AT, now);
                if (elapsed >= fuse) {
                    SpecialAbilities.bomb(level, z, fuse);
                } else {
                    // Derived, not accumulated: the belly swells linearly in real time, so a slowly
                    // inflating Bomber reads as "long fuse", and "long fuse" reads as "big explosion".
                    z.setAttached(SpecialAttachment.BOMBER_CHARGE, (float) elapsed / fuse);
                }
            }
            case SCREAMER -> {
                if (tgt != null && sz.pursuit().specialReady()) {
                    SpecialAbilities.hurl(sz, z, tgt, ctx);
                    sz.pursuit().resetSpecialCd();
                }
            }
            case HEALER -> {
                if (sz.pursuit().specialReady()) {
                    SpecialAbilities.heal(sz, z, ctx);
                    sz.pursuit().resetSpecialCd();
                }
            }
            case NECROMANCER -> {
                if (tgt != null && sz.pursuit().specialReady()) {
                    SpecialAbilities.summon(sz, level, z, ctx);
                    sz.pursuit().resetSpecialCd();
                }
            }
            default -> { }
        }
    }

    /** DEATH variants act as the zombie dies, while it is still where it stood. */
    public static void onDeath(Zombie z, ServerLevel level) {
        SpecialVariant v = SpecialVariantRegistry.byId(z.getAttached(SpecialAttachment.SPECIAL));
        if (v == null || v.kind() != SpecialVariant.Kind.DEATH) {
            return;
        }
        run(v, () -> v.behavior().onDeath(z, level));
    }
}
