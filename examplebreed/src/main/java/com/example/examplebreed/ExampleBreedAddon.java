package com.example.examplebreed;

import oas.dreyka.lethalbreed.api.LethalBreedAddon;
import oas.dreyka.lethalbreed.api.LethalBreedApi;
import oas.dreyka.lethalbreed.api.LethalBreedConfigApi;
import oas.dreyka.lethalbreed.api.LethalBreedState;
import oas.dreyka.lethalbreed.api.OptionBounds;
import oas.dreyka.lethalbreed.api.event.SpawnCullCallback;
import oas.dreyka.lethalbreed.api.event.TargetCandidateCallback;
import oas.dreyka.lethalbreed.api.variant.SpecialVariant;
import oas.dreyka.lethalbreed.api.variant.SpecialVariantRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A zombie that chills what it hunts, plus a creature the horde is asked to leave alone.
 *
 * <p>Nothing here reads a line of LethalBreed's source. What it costs to plug in is one entrypoint in
 * fabric.mod.json, the register calls below, and a lang key.
 */
public final class ExampleBreedAddon implements LethalBreedAddon {

    static final Logger LOGGER = LoggerFactory.getLogger("ExampleBreed");

    /** Namespaced so no shipped id can collide with it, and so the save says whose variant it is. */
    static final String FROSTBITE = "examplebreed:frostbite";

    @Override
    public void onLethalBreedReady() {
        // Our four options join the same file, with bounds, on a tab of our own. Called from here and
        // nowhere else: this entry point runs before the file is read, which is the only moment it works.
        LethalBreedConfigApi.register("frostbite", "Frostbite", FrostbiteConfig.class,
                new OptionBounds("frostbitePhase", 0, 1000),
                new OptionBounds("frostbiteWeight", 0, 1000),
                new OptionBounds("frostbiteRadius", 0, 64),
                new OptionBounds("frostbiteSlowSeconds", 1, 600));

        // A ninth variant, with its behaviour, its name, its phase and its weight. Suppliers, not numbers:
        // an admin editing the two values moves it without a restart.
        SpecialVariantRegistry.register(new SpecialVariant(
                FROSTBITE,
                SpecialVariant.Kind.ACTIVE,
                () -> FrostbiteConfig.frostbitePhase,
                () -> FrostbiteConfig.frostbiteWeight,
                new FrostbiteBehaviour()));

        // Strays are MobCategory.MONSTER and are not the plain Zombie class, so the shipped filter destroys
        // them at load, on a world already full of them. One line takes them back. The same thing without
        // Java would be a tag file, but the tag is LethalBreed's, so it goes in our own jar at
        // data/lethalbreed/tags/entity_type/spawn_protected.json listing "minecraft:stray".
        SpawnCullCallback.EVENT.register((entity, phase, proposed) ->
                entity.getType() == EntityType.STRAY ? false : proposed);

        // A rule of the mod, intercepted: nothing of ours is prey.
        TargetCandidateCallback.EVENT.register((self, candidate, proposed) ->
                ours(candidate) ? false : proposed);

        // Reading the mod's state, at a moment we did not choose.
        LethalBreedApi.onPhaseChanged((from, to) -> LOGGER.info(
                "phase {} -> {}, {} zombies tracked", from, to, LethalBreedState.trackedZombieCount()));
    }

    /** Anything that needs to READ a config value waits for the file. This runs after the load. */
    @Override
    public void onConfigLoaded() {
        LOGGER.info("frostbite unlocks at phase {}, weight {}",
                FrostbiteConfig.frostbitePhase, FrostbiteConfig.frostbiteWeight);
    }

    private static boolean ours(Entity e) {
        return e != null && "examplebreed".equals(
                BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace());
    }
}
