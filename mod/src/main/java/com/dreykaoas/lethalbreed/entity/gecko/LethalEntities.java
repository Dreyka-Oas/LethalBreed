package com.dreykaoas.lethalbreed.entity.gecko;

import com.dreykaoas.lethalbreed.LethalBreed;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Registration holder for the whole horror-zombie roster (5 GeckoLib-animated variants) + their spawn eggs.
 * Every variant shares the {@link HorrorZombie} class — its distinct body/animations come from its own
 * {@code .geo.json}/{@code .animation.json}/texture bound in the client renderer, and its distinct stats from
 * the per-variant attributes below. Run during common init from {@code BootstrapInit}. Kept whole by ProGuard.
 */
public final class LethalEntities {
    private LethalEntities() {}

    public static EntityType<HorrorZombie> HORROR_ZOMBIE, ECORCHE, BOURSOUFLE, RAMPANT, EMPALE,
            DIFFORME, PENDU, COLOSSE, EMACIE, BRULE;

    /** (type, resource id) for each variant in roster order — the client uses this to bind one renderer each. */
    public static final List<Variant> VARIANTS = new ArrayList<>();
    private static final List<Item> SPAWN_EGGS = new ArrayList<>();

    public record Variant(EntityType<HorrorZombie> type, String id) {}

    public static void register() {
        //                     id              width height   hp  dmg  speed knockback   (vanilla zombie ~0.23)
        HORROR_ZOMBIE = reg("horror_zombie", 0.7f, 2.0f, attrs(40, 7, 0.22, 0.5));   // broken / hunched
        ECORCHE       = reg("ecorche",       0.6f, 2.1f, attrs(30, 6, 0.27, 0.0));   // flayed skinless
        BOURSOUFLE    = reg("boursoufle",    1.0f, 2.0f, attrs(60, 8, 0.13, 0.8));   // bloated tank
        RAMPANT       = reg("rampant",       1.0f, 0.9f, attrs(26, 6, 0.20, 0.0));   // crawler
        EMPALE        = reg("empale",        0.7f, 2.1f, attrs(45, 7, 0.19, 0.7));   // spined
        DIFFORME      = reg("difforme",      1.0f, 2.0f, attrs(55, 10, 0.18, 0.6));  // giant torn arm
        PENDU         = reg("pendu",         0.7f, 2.0f, attrs(35, 6, 0.21, 0.2));   // broken neck
        COLOSSE       = reg("colosse",       1.2f, 2.9f, attrs(100, 12, 0.15, 0.9)); // huge tank
        EMACIE        = reg("emacie",        0.5f, 2.1f, attrs(18, 5, 0.30, 0.0));   // emaciated, fragile
        BRULE         = reg("brule",         0.6f, 2.0f, attrs(30, 7, 0.24, 0.2));   // charred

        for (Variant v : VARIANTS) {
            egg(v.id(), v.type());
        }
        // Drop all five spawn eggs into the vanilla Spawn Eggs creative tab.
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> {
            for (Item egg : SPAWN_EGGS) {
                entries.accept(egg);
            }
        });
    }

    private static EntityType<HorrorZombie> reg(String id, float w, float h, AttributeSupplier.Builder attrs) {
        Identifier rid = Identifier.fromNamespaceAndPath(LethalBreed.MOD_ID, id);
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, rid);
        EntityType<HorrorZombie> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, rid,
                EntityType.Builder.of(HorrorZombie::new, MobCategory.MONSTER).sized(w, h).build(key));
        FabricDefaultAttributeRegistry.register(type, attrs);
        VARIANTS.add(new Variant(type, id));
        return type;
    }

    private static void egg(String id, EntityType<HorrorZombie> type) {
        Identifier rid = Identifier.fromNamespaceAndPath(LethalBreed.MOD_ID, id + "_spawn_egg");
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, rid);
        SpawnEggItem item = new SpawnEggItem(new Item.Properties().spawnEgg(type).setId(key));
        Registry.register(BuiltInRegistries.ITEM, rid, item);
        SPAWN_EGGS.add(item);
    }

    /** Base zombie attributes with the shared no-reinforcement / long-sight tweaks, plus per-variant stats. */
    private static AttributeSupplier.Builder attrs(double hp, double dmg, double speed, double kb) {
        return HorrorZombie.createAttributes()
                .add(Attributes.MAX_HEALTH, hp)
                .add(Attributes.ATTACK_DAMAGE, dmg)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.KNOCKBACK_RESISTANCE, kb);
    }
}
