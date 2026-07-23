# Sleeping-Zombie Eyes-Closed + Arms-Lowered Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a LethalBreed zombie is day-sleeping (`ZombieState.SLEEPING`), render it with its arms lowered along its body and an eyelids overlay drawn over its eyes so it visibly looks asleep.

**Architecture:** The sleep state already exists server-side (`ZombieState.SLEEPING`, written in `SmartZombie.setState`) but is **not** synced to the client, so the render thread can't react to it. We follow the mod's existing BOMBEUR blueprint end-to-end: (1) add a **synced** boolean attachment for "sleeping"; (2) carry it onto the `LivingEntityRenderState` via the existing `BellyChargeHolder` duck interface, populated in `LivingEntityRenderer.extractRenderState`; (3) a `setupAnim` mixin on `AbstractZombieModel` lowers the arms when the flag is set; (4) an `EyesLayer` subclass registered on `ZombieRenderer` draws a mostly-transparent eyelids texture over the model, gated on the same flag.

**Tech Stack:** Java 21, Fabric Loom, MC 1.21.11 (official Mojang mappings), SpongePowered Mixin 0.8.7, Fabric data-attachment API (`net.fabricmc.fabric.api.attachment.v1`), 1.21.x render-state + `submit(...)`/`SubmitNodeCollector` rendering pipeline.

## Global Constraints

- MC version: **1.21.11**, Mojang official mappings (`loom.officialMojangMappings()`) — use mapped names verbatim (e.g. `Identifier`, not `ResourceLocation`; `AbstractZombieModel`, `ZombieRenderState`, `ZombieModel`).
- Namespace for all identifiers/assets: **`lethalbreed`**.
- Mixins live in package `com.dreykaoas.lethalbreed.mixin` (server) / `com.dreykaoas.lethalbreed.mixin.client` (client) and MUST be registered in `src/main/resources/lethalbreed.mixins.json` or they silently never apply. `injectors.defaultRequire` is `1` and `required` is `true` → a mixin whose target method can't be resolved is a HARD crash at load, so **run the client after every mixin change** (compile-green ≠ works).
- Types referenced by transformed/mixin code must NOT live inside the `mixin` package (Mixin throws `IllegalClassLoadError`) — that's why the render-state duck interface lives in `com.dreykaoas.lethalbreed.client.BellyChargeHolder`. Add new render-state accessors there, not in a mixin class.
- Model parts (`body`, `rightArm`, …) are **shared across all zombies** and re-posed by vanilla `setupAnim` every frame; our `@At("TAIL")` hooks must therefore write the pose for sleeping zombies AND rely on vanilla resetting it for non-sleeping ones (never leave a shared part mutated on a non-sleeping frame — set it only when sleeping, exactly like `ZombieBellyModelMixin`).
- This mod has no unit tests for rendering. Verification is: `./gradlew compileJava` (green) + `./gradlew runClient` (boots, no mixin crash) for plumbing tasks, and an in-game screenshot on the `Greenfield v0.5.4` save for visual tasks. That matches the established codebase practice (see `run/screenshots/`, the `dev-tests`/`run-all-tests` skills). Do not fabricate JUnit tests for the render path.

---

## File Structure

**Modify:**
- `src/main/java/com/dreykaoas/lethalbreed/entity/ZombieStateAttachment.java` — add a synced `SLEEPING` boolean attachment (client-visible), alongside the existing server-only `STATE`.
- `src/main/java/com/dreykaoas/lethalbreed/entity/SmartZombie.java` (`setState`, ~line 70-77) — mirror `state == SLEEPING` into the new synced attachment.
- `src/main/java/com/dreykaoas/lethalbreed/client/BellyChargeHolder.java` — add `lethalbreed$sleeping()` getter/setter to the duck interface.
- `src/main/java/com/dreykaoas/lethalbreed/mixin/client/LivingEntityRenderStateMixin.java` — add the `@Unique boolean lethalbreed$sleeping` field + accessors.
- `src/main/java/com/dreykaoas/lethalbreed/mixin/client/LivingEntityRendererMixin.java` (`extractRenderState` TAIL) — copy the synced flag onto the render state.
- `src/main/resources/lethalbreed.mixins.json` — register the two new client mixins.

**Create:**
- `src/main/java/com/dreykaoas/lethalbreed/mixin/client/ZombieSleepArmsMixin.java` — lowers arms when sleeping.
- `src/main/java/com/dreykaoas/lethalbreed/client/ZombieSleepEyesLayer.java` — `EyesLayer` subclass drawing the eyelids overlay when sleeping.
- `src/main/java/com/dreykaoas/lethalbreed/mixin/client/ZombieRendererEyesLayerMixin.java` — registers `ZombieSleepEyesLayer` on `ZombieRenderer`.
- `src/main/resources/assets/lethalbreed/textures/entity/zombie_sleep_eyes.png` — 64×64 overlay, transparent except eyelid pixels.

**Task order & independence:** Tasks 1→2 are invisible plumbing (verified by compile+boot). Task 3 (arms) is the first visible result and is independently shippable. Task 4 (eyes) is independently shippable on top of 1-2. Ship 3 before 4.

---

### Task 1: Sync a client-visible "sleeping" flag

**Files:**
- Modify: `src/main/java/com/dreykaoas/lethalbreed/entity/ZombieStateAttachment.java`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/entity/SmartZombie.java:70-77`

**Interfaces:**
- Produces: `ZombieStateAttachment.SLEEPING` — `AttachmentType<Boolean>`, synced to all tracking clients, `true` iff the zombie is currently in `ZombieState.SLEEPING`. Read client-side in Task 2 via `entity.getAttachedOrElse(ZombieStateAttachment.SLEEPING, false)`.

- [ ] **Step 1: Add the synced boolean attachment**

In `ZombieStateAttachment.java`, add the imports and the new field next to `STATE`:

```java
// add these imports at the top, next to the existing AttachmentRegistry/AttachmentType imports:
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.minecraft.network.codec.ByteBufCodecs;
```

```java
    /**
     * Whether this zombie is CURRENTLY day-sleeping ({@link ZombieState#SLEEPING}). Unlike {@link #STATE}
     * (server-only), this one is SYNCED to tracking clients so the renderer can pose the zombie as asleep
     * (arms lowered, eyes closed). Transient — a fresh zombie starts awake (false). Written on every state
     * change in {@link SmartZombie#setState}.
     */
    public static final AttachmentType<Boolean> SLEEPING = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath("lethalbreed", "sleeping"),
            builder -> builder
                    .initializer(() -> false)
                    .syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
```

`init()` already force-loads this class (called from `BootstrapInit.run()`), so no registration wiring changes.

- [ ] **Step 2: Mirror the sleep state into the synced attachment on every state change**

In `SmartZombie.java`, extend `setState` (currently only writes `STATE`):

```java
    public void setState(ZombieState state) {
        if (this.state != state) {
            this.state = state;
            // Record the authoritative behaviour state so server-side systems can react to it (e.g. a dozing
            // zombie stays silent via ZombieSleepSilenceMixin). Only on change (no spam).
            entity.setAttached(ZombieStateAttachment.STATE, state.ordinal());
            // Mirror the sleeping bit into the SYNCED attachment so the client renderer can pose the zombie
            // as asleep. Written here (on change only) so both the server STATE and the client flag stay in
            // lockstep.
            entity.setAttached(ZombieStateAttachment.SLEEPING, state == ZombieState.SLEEPING);
        }
    }
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --console=plain -q`
Expected: no errors (only the pre-existing deprecation notes).

- [ ] **Step 4: Boot check (attachment registers, no crash)**

Run: `./gradlew runClient --console=plain` (launch, let it reach the main menu, then quit), OR reuse the running dev client.
Expected: no `Registry`/attachment errors; game reaches menu. The flag has no visible effect yet — that arrives in Task 3.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/dreykaoas/lethalbreed/entity/ZombieStateAttachment.java src/main/java/com/dreykaoas/lethalbreed/entity/SmartZombie.java
git commit -m "feat(sleep): sync a client-visible SLEEPING attachment"
```

---

### Task 2: Carry the sleeping flag onto the render state

**Files:**
- Modify: `src/main/java/com/dreykaoas/lethalbreed/client/BellyChargeHolder.java`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/mixin/client/LivingEntityRenderStateMixin.java`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/mixin/client/LivingEntityRendererMixin.java`

**Interfaces:**
- Consumes: `ZombieStateAttachment.SLEEPING` (Task 1).
- Produces: `((BellyChargeHolder) renderState).lethalbreed$sleeping()` → `boolean`, readable from any zombie model/layer hook that has the render state. `true` iff the zombie is asleep.

- [ ] **Step 1: Add the accessor to the duck interface**

In `BellyChargeHolder.java`, add below the existing methods:

```java
    /** True when the zombie this render state belongs to is day-sleeping — pose it asleep (arms down, eyes
     *  closed). Set in {@code LivingEntityRendererMixin.extractRenderState}, read in the zombie arms/eyes hooks. */
    boolean lethalbreed$sleeping();

    void lethalbreed$sleeping(boolean sleeping);
```

- [ ] **Step 2: Implement the field on the render-state mixin**

In `LivingEntityRenderStateMixin.java`, add next to the existing `@Unique` fields and accessors:

```java
    @Unique
    private boolean lethalbreed$sleeping;
```

```java
    @Override
    public boolean lethalbreed$sleeping() {
        return lethalbreed$sleeping;
    }

    @Override
    public void lethalbreed$sleeping(boolean sleeping) {
        this.lethalbreed$sleeping = sleeping;
    }
```

- [ ] **Step 3: Populate it in the extract hook**

In `LivingEntityRendererMixin.java`, add the import and extend the existing `lethalbreed$carryBellyCharge` method body (it already guards `instanceof Zombie` and runs every frame):

```java
// add import:
import com.dreykaoas.lethalbreed.entity.ZombieStateAttachment;
```

```java
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void lethalbreed$carryBellyCharge(LivingEntity entity, LivingEntityRenderState state,
                                              float partialTick, CallbackInfo ci) {
        float charge = entity instanceof Zombie
                ? entity.getAttachedOrElse(SpecialAttachment.BOMBEUR_CHARGE, 0.0f)
                : 0.0f;
        ((BellyChargeHolder) state).lethalbreed$bellyCharge(charge);

        boolean sleeping = entity instanceof Zombie
                && entity.getAttachedOrElse(ZombieStateAttachment.SLEEPING, false);
        ((BellyChargeHolder) state).lethalbreed$sleeping(sleeping);
    }
```

- [ ] **Step 4: Compile**

Run: `./gradlew compileJava --console=plain -q`
Expected: no errors.

- [ ] **Step 5: Boot check**

Run: `./gradlew runClient` to the menu (or reuse running client). Expected: no crash. Still no visible change (nothing reads the render-state flag yet).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/dreykaoas/lethalbreed/client/BellyChargeHolder.java src/main/java/com/dreykaoas/lethalbreed/mixin/client/LivingEntityRenderStateMixin.java src/main/java/com/dreykaoas/lethalbreed/mixin/client/LivingEntityRendererMixin.java
git commit -m "feat(sleep): carry sleeping flag onto LivingEntityRenderState"
```

---

### Task 3: Lower the arms while sleeping (first visible result)

**Files:**
- Create: `src/main/java/com/dreykaoas/lethalbreed/mixin/client/ZombieSleepArmsMixin.java`
- Modify: `src/main/resources/lethalbreed.mixins.json` (register it)

**Interfaces:**
- Consumes: `((BellyChargeHolder) state).lethalbreed$sleeping()` (Task 2).

- [ ] **Step 1: Create the arms mixin**

Follow the exact idiom of `ZombieBellyModelMixin` (same target class, same `setupAnim(ZombieRenderState)` TAIL, same `(HumanoidModel<?>)(Object)this` cast to reach the shared parts). `AbstractZombieModel` extends `HumanoidModel`, whose `rightArm`/`leftArm` are public `ModelPart`s. Vanilla poses zombie arms raised-forward (`xRot ≈ -1.5`); setting `xRot = 0` and zeroing yaw/roll drops them straight along the body.

Create `ZombieSleepArmsMixin.java`:

```java
package com.dreykaoas.lethalbreed.mixin.client;

import com.dreykaoas.lethalbreed.client.BellyChargeHolder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.zombie.AbstractZombieModel;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops a day-sleeping zombie's arms to rest along its body instead of the vanilla raised-forward pose.
 * Runs at TAIL of the shared-model {@code setupAnim}, AFTER vanilla has set the pose, and only writes the
 * arm rotations when the render state says this zombie is asleep — non-sleeping frames keep vanilla's pose
 * (the parts are shared across all zombies, so we must not mutate them otherwise). Mirrors the belly hook.
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractZombieModel.class)
public class ZombieSleepArmsMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)V",
            at = @At("TAIL"))
    private void lethalbreed$sleepArms(ZombieRenderState state, CallbackInfo ci) {
        if (!((BellyChargeHolder) state).lethalbreed$sleeping()) {
            return;
        }
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        model.rightArm.xRot = 0.0f;
        model.leftArm.xRot = 0.0f;
        model.rightArm.yRot = 0.0f;
        model.leftArm.yRot = 0.0f;
        model.rightArm.zRot = 0.0f;
        model.leftArm.zRot = 0.0f;
    }
}
```

- [ ] **Step 2: Register the mixin**

In `lethalbreed.mixins.json`, add to the `"client"` array (order doesn't matter):

```json
    "client.ZombieSleepArmsMixin",
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --console=plain -q`
Expected: no errors.

- [ ] **Step 4: In-game visual verification (this is the test)**

Run the client on the test save and force a zombie to sleep:

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
log=/tmp/lb-armtest.log; rm -f "$log"
setsid ./gradlew runClient --console=plain > "$log" 2>&1 < /dev/null &
```

Wait for `phase loaded:` in `$log` (world = `Greenfield v0.5.4`). Then in-game, open chat and run:
- `/lethalphase 1`  ← below `dayAwakePhaseStart` (10) so 0% stay awake → all zombies day-sleep
- `/time set day`
- `/weather clear`
- `/lethalspawn minecraft:zombie 6 4`

Watch a spawned zombie: once it grounds and stops moving it enters `ZombieState.SLEEPING` (`ZombieMood.dozeInPlace`, sets `NoAi` + head bowed). Take a screenshot (F2; saved to `run/screenshots/`).

Expected PASS: the sleeping zombie's arms hang **down along its body** (not raised forward). A still-awake/pursuing zombie (e.g. hit one to wake it, or one that hasn't dozed yet) keeps the normal raised arms — confirms the guard is conditional, not global.

Read the newest screenshot to confirm:
```bash
ls -t /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod/run/screenshots/*.png | head -1
```
(Open it / Read it and verify arms are down.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/dreykaoas/lethalbreed/mixin/client/ZombieSleepArmsMixin.java src/main/resources/lethalbreed.mixins.json
git commit -m "feat(sleep): lower zombie arms while day-sleeping"
```

---

### Task 4: Eyes-closed overlay while sleeping

**Files:**
- Create: `src/main/resources/assets/lethalbreed/textures/entity/zombie_sleep_eyes.png`
- Create: `src/main/java/com/dreykaoas/lethalbreed/client/ZombieSleepEyesLayer.java`
- Create: `src/main/java/com/dreykaoas/lethalbreed/mixin/client/ZombieRendererEyesLayerMixin.java`
- Modify: `src/main/resources/lethalbreed.mixins.json` (register the renderer mixin)

**Interfaces:**
- Consumes: `((BellyChargeHolder) state).lethalbreed$sleeping()` (Task 2); the vanilla `EyesLayer<S,M>` machinery (renders the parent model with `renderType()` at full brightness, so a mostly-transparent texture shows only its opaque pixels — here, the eyelids).

- [ ] **Step 1: Generate the eyelids overlay texture**

The overlay is a 64×64 zombie skin sheet that is fully transparent EXCEPT for eyelid pixels over the two eye positions on the head's front face. On the vanilla 64×64 humanoid skin the head front face occupies pixels x∈[8,15], y∈[8,15]; the zombie's eyes sit on rows y≈10–11 (right eye near x≈9–10, left eye near x≈13–14). Draw a short dark row over each eye. This first pass is deliberately approximate and **will be tuned in Step 4**.

Run this generator (writes the PNG directly — no external assets needed):

```bash
python3 - <<'PY'
from PIL import Image
img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
px = img.load()
# eyelid colour: dark desaturated green, matching a closed zombie lid; fully opaque
lid = (40, 58, 40, 255)
# head FRONT face is x[8..15], y[8..15]; eyes ~ y 10..11. Right eye x 9..10, left eye x 13..14.
for x in range(9, 11):      # right eye
    for y in range(10, 12):
        px[x, y] = lid
for x in range(13, 15):     # left eye
    for y in range(10, 12):
        px[x, y] = lid
out = "/run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod/src/main/resources/assets/lethalbreed/textures/entity/zombie_sleep_eyes.png"
import os; os.makedirs(os.path.dirname(out), exist_ok=True)
img.save(out)
print("wrote", out)
PY
```

(If `PIL` is unavailable, `pip install Pillow` first, or generate an equivalent 64×64 RGBA PNG by any means — the only requirement is opaque eyelid pixels over the eye region and transparency everywhere else.)

- [ ] **Step 2: Get the exact vanilla EyesLayer template**

The 1.21.11 render pipeline uses `submit(PoseStack, SubmitNodeCollector, int, S, float, float)` and `EyesLayer` has an abstract `renderType()`. Generate the decompiled sources once and read the vanilla template so the subclass mirrors the exact `RenderType` factory (the render-type package moved to `net.minecraft.client.renderer.rendertype.RenderType` in this version):

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
./gradlew genSources --console=plain -q
SRC=$(find ~/.gradle/caches/fabric-loom -iname "minecraft-merged*sources*.jar" | head -1)
unzip -p "$SRC" net/minecraft/client/renderer/entity/layers/EyesLayer.java
unzip -p "$SRC" net/minecraft/client/renderer/entity/layers/SpiderEyesLayer.java   # a concrete subclass = the renderType() idiom to copy
```

Note the exact `RenderType` factory call used by `SpiderEyesLayer.renderType()` (e.g. `RenderType.eyes(Identifier)`), and confirm the `EyesLayer` type parameters and `submit` signature. Use them verbatim in Step 3.

- [ ] **Step 3: Create the eyes layer (conditional subclass of EyesLayer)**

`EyesLayer` renders the parent model with `renderType()` unconditionally each frame; we override `submit` to no-op unless the render state says the zombie is asleep, then delegate to `super.submit(...)`. Fill `renderType()` from the vanilla idiom read in Step 2.

Create `ZombieSleepEyesLayer.java` (lives in `client`, NOT the `mixin` package, so mixin-transformed code may reference it):

```java
package com.dreykaoas.lethalbreed.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Draws a closed-eyelids overlay over a day-sleeping zombie. Subclasses vanilla {@link EyesLayer}, whose
 * machinery renders the parent model with {@link #renderType()}; our overlay texture is transparent except
 * for the eyelid pixels, so only the lids show. Gated on the synced sleeping flag ({@link BellyChargeHolder})
 * so it appears only while the zombie is asleep.
 */
@Environment(EnvType.CLIENT)
public class ZombieSleepEyesLayer extends EyesLayer<ZombieRenderState, ZombieModel<ZombieRenderState>> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("lethalbreed", "textures/entity/zombie_sleep_eyes.png");

    // Fill this factory from the vanilla SpiderEyesLayer.renderType() read in Step 2 (e.g. RenderType.eyes).
    private static final RenderType RENDER_TYPE = RenderType.eyes(TEXTURE);

    public ZombieSleepEyesLayer(RenderLayerParent<ZombieRenderState, ZombieModel<ZombieRenderState>> parent) {
        super(parent);
    }

    @Override
    public RenderType renderType() {
        return RENDER_TYPE;
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, ZombieRenderState state,
                       float yRot, float xRot) {
        if (!((BellyChargeHolder) state).lethalbreed$sleeping()) {
            return;
        }
        super.submit(pose, collector, light, state, yRot, xRot);
    }
}
```

If Step 2 shows the `submit` signature differs (arg order/types), match it exactly — `@Override` will fail to compile if it's wrong, which is the check.

- [ ] **Step 4: Register the layer on the zombie renderer**

`ZombieRenderer` extends `AbstractZombieRenderer<Zombie, ZombieRenderState, ZombieModel<ZombieRenderState>>` and has the constructor `ZombieRenderer(EntityRendererProvider.Context)`. Add the layer at construction via a mixin. `addLayer` is inherited from `LivingEntityRenderer` (protected); call it through an accessor cast or directly if visible to the mixin (it is — the mixin is logically inside the renderer). Create `ZombieRendererEyesLayerMixin.java`:

```java
package com.dreykaoas.lethalbreed.mixin.client;

import com.dreykaoas.lethalbreed.client.ZombieSleepEyesLayer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the closed-eyelids overlay layer to the real-zombie renderer at construction, so a day-sleeping
 * zombie renders with its eyes shut. The layer itself is a no-op unless the render state's sleeping flag is set.
 */
@Environment(EnvType.CLIENT)
@Mixin(ZombieRenderer.class)
public abstract class ZombieRendererEyesLayerMixin
        extends LivingEntityRenderer<Zombie, ZombieRenderState, ZombieModel<ZombieRenderState>> {

    // Required because we extend the generic LivingEntityRenderer; never invoked (mixin superclass shim).
    private ZombieRendererEyesLayerMixin() { super(null); }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)V",
            at = @At("TAIL"))
    private void lethalbreed$addSleepEyesLayer(EntityRendererProvider.Context ctx, CallbackInfo ci) {
        this.addLayer(new ZombieSleepEyesLayer(this));
    }
}
```

If `addLayer` is not accessible via the superclass shim, instead add a `MobRendererAddLayerAccessor`/`@Invoker` for `addLayer` — but the `extends LivingEntityRenderer<...>` shim above exposes the protected `addLayer` directly and is the least-code path; try it first (compile will tell you).

- [ ] **Step 5: Register the renderer mixin**

In `lethalbreed.mixins.json` `"client"` array add:

```json
    "client.ZombieRendererEyesLayerMixin",
```

- [ ] **Step 6: Compile**

Run: `./gradlew compileJava --console=plain -q`
Expected: no errors. (If `renderType()`/`submit` overrides don't match, fix the signatures against the Step 2 vanilla source.)

- [ ] **Step 7: In-game visual verification + texture tuning (the test)**

Launch the client, force sleep exactly as in Task 3 Step 4 (`/lethalphase 1`, `/time set day`, `/lethalspawn minecraft:zombie 6 4`), let a zombie doze, screenshot.

Expected PASS: the sleeping zombie's eyes appear **closed** (eyelid pixels over the eyes); an awake zombie's eyes are normal. If the lid pixels are misaligned (covering forehead/cheeks or missing the eyes), adjust the `x`/`y` ranges in the Step 1 generator, re-run it, and relaunch — iterate until the lids sit on the eyes. Confirm from the newest screenshot:

```bash
ls -t /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod/run/screenshots/*.png | head -1
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/dreykaoas/lethalbreed/client/ZombieSleepEyesLayer.java src/main/java/com/dreykaoas/lethalbreed/mixin/client/ZombieRendererEyesLayerMixin.java src/main/resources/lethalbreed.mixins.json src/main/resources/assets/lethalbreed/textures/entity/zombie_sleep_eyes.png
git commit -m "feat(sleep): closed-eyelids overlay on day-sleeping zombies"
```

---

## Self-Review

**Spec coverage:**
- "Lower arms when sleeping" → Task 3. ✅
- "Add a texture over the eyes when sleeping" (user's chosen approach: overlay, not full-texture swap) → Task 4 (transparent overlay via `EyesLayer` machinery). ✅
- Sleep state reaching the client (the actual root blocker) → Tasks 1–2. ✅
- Arms "along the body" (user's chosen pose) → Task 3 sets `xRot=0` + zeroed yaw/roll. ✅

**Placeholder scan:** The only intentionally deferred exact-code spots are (a) the `RenderType` factory in `ZombieSleepEyesLayer` and (b) the `submit` signature — both are pinned to a concrete "read vanilla `SpiderEyesLayer`/`EyesLayer` via genSources in Step 2, then match verbatim" instruction, with a compile-time `@Override` check catching any mismatch. This is a real reference-implementation lookup, not a vague TODO. The texture is generated by a complete runnable script, not hand-waved.

**Type consistency:** `lethalbreed$sleeping()`/`lethalbreed$sleeping(boolean)` names match across `BellyChargeHolder`, `LivingEntityRenderStateMixin`, `LivingEntityRendererMixin`, `ZombieSleepArmsMixin`, `ZombieSleepEyesLayer`. `ZombieStateAttachment.SLEEPING` type `AttachmentType<Boolean>` read with `getAttachedOrElse(..., false)`. Render/model generics `ZombieRenderState` + `ZombieModel<ZombieRenderState>` match the `ZombieRenderer` supertype signature confirmed via javap.

**Risks / notes:**
- `EyesLayer` renders at full brightness (emissive). Eyelids will be fully lit; in daytime (when zombies sleep) this is visually fine. If a non-glowing lid is wanted later, swap `renderType()` to a cutout/translucent type (e.g. the `coloredCutoutModelCopyLayerRender` static helper on `RenderLayer`) — a follow-up, not part of this spec.
- Husk (`instanceof Zombie`) also gets the arms/eyes hooks since `AbstractZombieModel` is shared and Husk extends Zombie — desirable (a sleeping Husk should also look asleep). Drowned is discarded by the mod, so irrelevant.
- `PlayerModelZombieArmsMixin` (hallucination) is unrelated and untouched — it targets `PlayerModel`, not `AbstractZombieModel`.
