/**
 * Project: Lethal Breed
 * Responsibility: Multi-threaded Execution Management
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.ai;

import java.util.concurrent.*;

public class LethalThreads {
    private static final int CORES = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
        CORES, CORES, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(256), // Increased to support more zombies
        new ThreadPoolExecutor.DiscardOldestPolicy() // Discard oldest calculations if full
    );

    public static void execute(Runnable task) {
        POOL.execute(task);
    }
    
    public static void shutdown() {
        POOL.shutdown();
    }
}