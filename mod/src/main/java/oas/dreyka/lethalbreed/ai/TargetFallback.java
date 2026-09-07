package oas.dreyka.lethalbreed.ai;

import oas.dreyka.lethalbreed.config.domain.TargetingConfig;
import oas.dreyka.lethalbreed.entity.LodLevel;
import oas.dreyka.lethalbreed.entity.SmartZombie;

import net.minecraft.server.level.ServerLevel;

/**
 * What a zombie does when nothing is detected: walk to the spot it last heard something, follow its pack's
 * waypoint, or freeze.
 *
 * <p>Split from {@link LodManager}, which keeps the live-detection branch. These three are the fallbacks,
 * in strict priority order, and each of them has to cut the vanilla attack target as well as our own: the
 * mod declaring a zombie frozen while vanilla's never-stripped {@code ZombieAttackGoal} keeps driving it is
 * exactly the divergence that used to cap a Screamer's rally.
 */
final class TargetFallback {
    private TargetFallback() {}

    /** @return the LOD tier for a zombie with no live target. */
    static LodLevel classify(SmartZombie sz, ServerLevel level, LodLevel prev) {
        if (TargetingConfig.targetMemoryTicks > 0 && sz.pursuit().hasMemory()
                && level.getGameTime() < sz.pursuit().memoryExpire()) {
            // Lost sight AND sound, but remember where it was: keep going there briefly (no live entity, so
            // no melee/vanilla target). Reached the spot with nothing there, or memory ran out → forget.
            sz.pursuit().setMemoryTarget();
            sz.entity().setTarget(null);
            double d = sz.pursuit().distanceToTargetSq();
            double arrive = TargetingConfig.soundArriveDistance;
            // Only "arrived, nothing here → forget" when the zombie can actually SEE the remembered spot. If an
            // opaque wall still stands between it and the spot (e.g. a trapped, noisy mob enclosed in blocks),
            // it is NOT arrived. Keep pursuing so it breaks through instead of giving up at the wall and
            // letting the half-broken block lapse. Memory still expires on its own timer above.
            boolean atSpot = d <= arrive * arrive && LodManager.canSeeSpot(level, sz.entity(),
                    sz.pursuit().tgtX(), sz.pursuit().tgtY(), sz.pursuit().tgtZ());
            // A day-sleep SHADE-seek isn't "arrived" until the zombie's OWN foot block is out of the sky: being
            // 2.5 blocks short with mere line-of-sight to the shade column still leaves it burning under open
            // sky, so clearing here would strand it (clear → re-find same shade → clear) in a visible stutter.
            // Ordinary noise memories keep the plain near+line-of-sight arrival.
            boolean arrived = atSpot
                    && (!sz.mood().isSeekingShade() || !level.canSeeSky(sz.entity().blockPosition()));
            if (arrived) {
                sz.pursuit().clearTarget();
                sz.pursuit().clearMemory();
                return LodLevel.FROZEN;
            } else {
                return LodManager.lodFromDistSq(d, prev);
            }
        } else if (sz.pursuit().pack().hasWaypoint()) {
            // Lowest priority: nothing seen, nothing remembered, but the zombie's pack wants it somewhere.
            // Deliberately NOT the memory slot (see {@link oas.dreyka.lethalbreed.pack.rule.PackTether}) so a pack march cannot be clobbered by a
            // passing cow or hijacked by a distress rally, and a marching member is not mistaken for one
            // investigating a noise (which would keep it awake through the day, burning in the sun).
            sz.pursuit().setPackTarget();
            sz.entity().setTarget(null);
            // No canSeeSpot here, unlike the memory branch: that is a level.clip raycast, and paying one per
            // marching member per activation would make the march the dominant cost of the whole system.
            // Arrival is PackMarch's business: it replants the waypoint every visit regardless.
            //
            // The waypoint is short-range by construction (packMarchLead, capped under lodLow). That is the
            // whole reason this classifies to HIGH/MEDIUM instead of FROZEN. Aiming a member at the pack's
            // actual destination hundreds of blocks away would freeze it, not move it.
            return LodManager.lodFromDistSq(sz.pursuit().distanceToTargetSq(), prev);
        } else {
            sz.pursuit().clearTarget();
            sz.pursuit().clearMemory();
            // Cut the vanilla target too, as the memory and pack branches above already do. Without it the
            // mod declares the zombie frozen and target-less while vanilla's ZombieAttackGoal, never
            // stripped, keeps driving it at whatever it last locked on, with no flow field, no breach, no
            // pillaring and no tick() of ours. That divergence is what caps a Screamer's rally: the zombies it
            // hands a target to are re-frozen here on their next classify, yet keep walking.
            sz.entity().setTarget(null);
            return LodLevel.FROZEN;
        }
    }
}
