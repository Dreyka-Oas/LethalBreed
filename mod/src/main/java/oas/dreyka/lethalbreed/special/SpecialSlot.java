package oas.dreyka.lethalbreed.special;

import oas.dreyka.lethalbreed.api.variant.SpecialVariant;
import oas.dreyka.lethalbreed.api.variant.SpecialVariantRegistry;
import oas.dreyka.lethalbreed.config.domain.SpecialVariantConfig;

import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * One zombie's variant and the cooldown that goes with it.
 *
 * <p>Lives apart from {@code ZombiePursuit}, which was at its file budget with no room for the accessor an
 * addon's variant needs. Splitting it out costs one field there and gives the variant a place of its own,
 * which is where it belonged: everything else on that class is about where the zombie is going.
 */
public final class SpecialSlot {

    // Null means no variant, which is most zombies. The old code used a NONE enum constant for this, and
    // that constant only existed because an enum has no way to say "nothing".
    private SpecialVariant variant;
    private boolean resolved;
    private int cooldown;

    public SpecialSlot(Zombie z) {
        this.variant = read(z);
    }

    public SpecialVariant variant() {
        return variant;
    }

    public boolean isActive() {
        return variant != null && variant.kind() == SpecialVariant.Kind.ACTIVE;
    }

    public boolean ready() {
        return cooldown <= 0;
    }

    /** One cooldown per zombie rather than one per variant, on the player-facing action interval. */
    public void resetCooldown() {
        cooldown = Math.max(1, SpecialVariantConfig.specialActionInterval);
    }

    /** Re-read the variant from the attachment, after something forced one onto the zombie. */
    public void refresh(Zombie z) {
        this.variant = read(z);
    }

    /** First-tick resolve, the attachment being reliably present by then, plus the cooldown countdown. */
    public void tick(Zombie z) {
        if (!resolved) {
            refresh(z);
            resolved = true;
        }
        if (cooldown > 0) {
            cooldown--;
        }
    }

    private static SpecialVariant read(Zombie z) {
        return SpecialVariantRegistry.byId(z.getAttached(SpecialAttachment.SPECIAL));
    }
}
