# Server Optimization

**Lethal Breed** is designed to maintain optimal performance even on servers with a high density of players and entities. To achieve this, the mod offloads the heaviest AI calculations to separate threads.

---

## 🧵 Multi-Thread Architecture

Unlike standard Minecraft behavior where AI runs entirely on the main thread (which can cause slow-downs or "TPS drops"), Lethal Breed uses an asynchronous task management system called **LethalThreads**.

### The Engine: `LethalThreads.java`
This component manages an intelligent queue and adapts its power based on the server's processor.

```java
public class LethalThreads {
    private static final int CORES = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
        CORES, CORES, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(256),
        new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    public static void execute(Runnable task) {
        POOL.execute(task);
    }
}
```

---

## 🧠 What is "Threaded" and why?

The most costly calculation for the server is **Dynamic Obstruction Analysis**. When a zombie is blocked, it must scan the environment from several angles and heights to decide whether to mine or build.

### Asynchronous Thinking Process
The `BrainProcessor` asks background threads to calculate the solution while the game continues to run.

```java
LethalThreads.execute(() -> {
    try {
        // Heavy environmental scan (Raycasting)
        BlockPos res = ObstructionAnalyzer.getHorizontal(world, zombie, target);
        resultSlot.set(res);
    } finally {
        thinking.set(false); // Releases the zombie's "thought"
    }
});
```

### Benefits for the server:
- **TPS Stability**: The server does not stop to wait for a zombie to find its path.
- **Lag Reduction**: Even with 100 zombies trying to break through your walls, Minecraft's physics engine remains fluid.
- **CPU Utilization**: The mod finally exploits modern multi-core processors, where Vanilla Minecraft is often limited to a single core.

---
