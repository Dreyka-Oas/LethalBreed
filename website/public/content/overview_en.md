# Lethal Breed - Technical Overview

## Introduction
**Lethal Breed** is a survival-horror mod for Minecraft (Fabric) that radically transforms zombie behavior. The goal is to make every night a strategic challenge where zombies don't just walk toward the player, but adapt their approach based on the environment.

---

## 🏗️ Obstacle Engineering (Advanced Pathfinding)

### 1. Dynamic Obstruction Analysis
The mod uses a specialized class, `ObstructionAnalyzer`, to precisely identify what is blocking the zombie via angular scanning.

### 2. Bridge Coordination (Bridging)
The `BridgeCoordinator` intervenes to place dirt blocks and create a passage over gaps.

### 3. Placement System: `BlockSetter`
Manages physical interaction with the world. It checks the validity of the location before materializing a block.
```java
public static void placeDirt(World world, BlockPos pos) {
    if (PlacementValidator.canPlaceAt(world, pos)) {
        world.setBlockState(pos, Blocks.DIRT.getDefaultState());
    }
}
```

### 4. Block Destruction: `BreakAction`
Zombies mine obstacles with a configurable speed multiplier. A visual cracking effect is synchronized with real progress.
```java
int maxTime = (int) (20 / (speed * ModConfig.INSTANCE.breakSpeedMultiplier)); 
int progress = (int) (((float)timer / maxTime) * 10);
world.setBlockBreakingInfo(zombie.getId(), target, progress);
```

### 5. Critical Void Detection: `MovementCoordinator`
Prevents zombies from accidentally falling. If a "Next Step" leads to a void, the zombie stops dead, centers itself, and triggers bridge construction.

---

## 🧠 Artificial Intelligence and Threads

### 6. State Machine: `BuildStateMachine`
Drives complex transitions between chasing, mining, and building.

```java
public void tick() {
    if (state == 2) processMining(); // Mining State
    if (state == 1) processBuilding(); // Building State
}
```

### 7. Asynchronous Processing: `LethalThreads`
To avoid slowing down the server, complex AI calculations (such as obstruction analysis) are offloaded to background threads.
```java
private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
    CORES, CORES, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(256)
);
```

### 8. Thought Processor: `BrainProcessor`
Links the game tick and threads. It allows zombies to "think" about their next block without blocking the physics engine.

### 9. Starting Conditions: `BuildConditions`
Defines if a zombie should start building. It checks vertical distance (`dy`) and the presence of holes before engaging building mode.

---

## 🧗 Movement and Pack Coordination

### 10. Vertical Climbing: `ClimbMover`
Adjusts velocity vectors to allow zombies to climb along the structures they build or natural walls.

### 11. Intelligent Pack Placement: `PackPlacementLogic`
Prevents 10 zombies from trying to build in the same place. If a zombie detects allies already building, it will look for an adjacent point (North, South, East, or West) to create a wider staircase.

### 12. Placement Validation: `PlacementValidator`
Safety preventing block placement outside world limits or in non-replaceable blocks.

### 13. Physical Centering: `ConstructionCoordinator`
Before each building action, the zombie is "snapped" to the center of its current block to guarantee its tower or bridge is perfectly aligned.

---

## 🔊 Sound Ecosystem and Instincts

### 14. Hearing System
Zombies track environmental sounds (footsteps, blocks) via the `HearingRegistry`.

### 15. Survival Instinct: `FleeExplosionGoal`
Normal zombies detect "Primed" allies (Kamikazes about to explode) and flee to avoid collateral damage.
```java
var list = world.getEntitiesByClass(ZombieEntity.class, range, z -> z.hasTag("lethal_primed"));
if (!list.isEmpty()) fleeFrom(list.get(0));
```

### 16. Kamikaze Overload
Visual details for kamikazes: flame particles and electrical sparks (`ELECTRIC_SPARK`) if the explosion power exceeds 1.7x normal.

---

## 🧬 Genetics and Mutation

### 17. Dynamic Specimen Scaling
Manipulation of `SCALE`, `MAX_HEALTH`, and `MOVEMENT_SPEED` attributes based on the size generated at spawn.

### 18. Panic Mechanics
Management of screams and alerting allies when health falls below a critical threshold.

---
Last Update: February 12, 2026
