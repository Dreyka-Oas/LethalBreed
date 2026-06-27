---
name: apply-entity-attributes-before-client-sync
description: Use when a modded mob shows a visible grow/shrink (or other attribute) animation right after it spawns in LethalBreed. Attributes like SCALE applied in ENTITY_LOAD arrive AFTER the client already has the entity, so the client interpolates the change. Apply them in finalizeSpawn (pre-sync) instead.
---

# Apply entity attributes BEFORE the client sees the entity

## Symptom

A freshly spawned zombie (spawn egg, natural, `/summon`, spawner) visibly **grows or shrinks** for a
fraction of a second after appearing. Same for any other attribute the client renders (size most
obviously). Reloaded chunks do NOT show it.

## Cause

`ZombieVariation.apply()` (random `SCALE`/speed/damage) was called from
`ServerEntityEvents.ENTITY_LOAD`. That event fires **after** `level.addFreshEntity(...)` — i.e. after the
entity has been tracked and the spawn packet sent to the client. The later `SCALE` attribute update then
arrives as a separate packet and the client **interpolates** default→final size = the grow animation.

## Fix

Apply size-affecting attributes inside `Zombie.finalizeSpawn(...)`, which runs **before**
`addFreshEntity`, so the entity is already at its final scale in the very first tracking packet.

- Fabric's `ServerLivingEntityEvents.MOB_FINALIZE_SPAWN` **does not exist** in the fabric-api version
  pinned here (checked the `fabric-entity-events` sources — only ALLOW_DAMAGE / AFTER_DAMAGE /
  ALLOW_DEATH / AFTER_DEATH / MOB_CONVERSION). So use a **Mixin** instead:

  ```java
  @Mixin(Zombie.class)
  public abstract class ZombieFinalizeSpawnMixin {
      @Inject(method = "finalizeSpawn", at = @At("TAIL"))
      private void lethalbreed$applyVariation(CallbackInfoReturnable<SpawnGroupData> cir) {
          ZombieVariation.apply((Zombie) (Object) this);
      }
  }
  ```
  Register it in `lethalbreed.mixins.json` (`"required": true` → a wrong target name crashes boot, which
  is the cheap way to know it bound).

- Keep discard/equipment-strip/registry work in `ENTITY_LOAD` — those must run for **every** load,
  including chunk reloads. Only the size application moves.

- `finalizeSpawn` does NOT fire on chunk reload, which is correct: reloaded zombies already carry the
  persisted permanent modifier in NBT (fixed modifier id, idempotent), so they show no resize.

## General rule

Anything the client renders from an entity attribute must be set **before the entity is added to the
world / tracked** — `finalizeSpawn` (or earlier), never a post-add lifecycle event. Post-add attribute
changes are sent as updates and the client animates them.

## Verify

Spawn-egg a zombie in front of the player: correct size from the first rendered frame, no grow/shrink.
