package oas.dreyka.lethalbreed.mixin.zombie;

import oas.dreyka.lethalbreed.GameState;
import oas.dreyka.lethalbreed.entity.SmartZombie;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the day-doze freeze out of the save file (audit #2).
 *
 * <p>The doze stops a zombie with {@code setNoAi(true)}, and vanilla persists that flag while the mod's
 * own "we set it" flag is not persisted. Releasing it when the mood object dies is too late for the write:
 * {@code PersistentEntitySectionManager.storeChunkSections} serialises the section BEFORE it unloads the
 * entities, so Fabric's {@code ENTITY_UNLOAD} (and with it {@code releaseAiHold}) runs on an entity already
 * written. The periodic autosave and {@code /save-all} never unload anything at all, so they had no release
 * to be late for. Every one of those paths goes through here instead, which is why the fix is on the write
 * and not on any of them.
 *
 * <p>{@code Mob.addAdditionalSaveData} writes {@code NoAI} last, and {@code Zombie} calls {@code super}
 * first, so a TAIL injection on the base method lands after the key and before anything the subclass adds.
 * {@link oas.dreyka.lethalbreed.entity.ZombieMood#holdsAiFreeze()} is what keeps a freeze somebody else
 * set, a map maker's prop or a harness, written exactly as vanilla wrote it.
 */
@Mixin(Mob.class)
public abstract class ZombieNoAiNotPersistedMixin {

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void lethalbreed$dropOurNoAi(ValueOutput output, CallbackInfo ci) {
        if (!((Object) this instanceof Zombie z)) {
            return;
        }
        SmartZombie sz = GameState.REGISTRY.get(z.getId());
        if (sz != null && sz.mood().holdsAiFreeze()) {
            output.putBoolean("NoAI", false);
        }
    }
}
