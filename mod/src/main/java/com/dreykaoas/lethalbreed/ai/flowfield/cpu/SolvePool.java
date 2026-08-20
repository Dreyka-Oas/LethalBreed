package com.dreykaoas.lethalbreed.ai.flowfield.cpu;

import com.dreykaoas.lethalbreed.config.domain.engine.FlowConfig;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;

/**
 * The worker pool the CPU flow-field solve runs on, and the one thing that makes it awkward: it has to be
 * rebuilt when {@link FlowConfig#flowCpuThreads} changes, so a GUI or command edit takes effect without a
 * JVM restart, while solves may already be running on the pool being replaced.
 */
final class SolvePool {
    private SolvePool() {}

    /** Daemon worker factory for the CPU solve pool. */
    private static final ForkJoinPool.ForkJoinWorkerThreadFactory SOLVE_FACTORY = pool -> {
        ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        t.setName("LethalBreed-CpuSolve");
        t.setDaemon(true);
        return t;
    };

    private static volatile ForkJoinPool solvePool;     // lazily built, rebuilt on a flowCpuThreads change
    private static volatile int solvePoolThreads = -1;   // thread count the current pool was sized for

    private static int resolveSolveThreads() {
        int cfg = FlowConfig.flowCpuThreads;
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        // cfg>0: honour the request but cap RELATIVE to the host cores (cores*4) — a ForkJoinPool can't exceed
        // its MAX_CAP and massive oversubscription only adds contention. cfg<=0: auto = cores-2. This is a
        // runtime-relative anti-oversubscription bound, not a static config range (those live in ConfigBounds).
        return cfg > 0 ? Math.min(cfg, cores * 4) : Math.max(1, cores - 2);
    }

    /** CPU solve pool, rebuilt when {@link FlowConfig#flowCpuThreads} changes so a GUI/command edit takes
     *  effect without a JVM restart. Rebuilds are rare (only on a config change) and the superseded pool is
     *  shut down. Synchronized because solves run on the 2-thread FlowField daemon pool — two solves could
     *  otherwise race to rebuild. */
    static synchronized ForkJoinPool get() {
        int want = resolveSolveThreads();
        if (solvePool == null || want != solvePoolThreads) {
            // Replace the pool but DO NOT shut the old one down: a concurrent solve on another dimension may
            // still hold a reference to it and be about to submit(), and shutdown() would make that submit()
            // throw RejectedExecutionException. Instead the pool is built with a short keep-alive, so once a
            // superseded pool goes idle its daemon workers terminate within a few seconds and the pool is
            // GC'd — bounded, no leak, no race. Rebuilds happen only when flowCpuThreads actually changes.
            solvePool = new ForkJoinPool(want, SOLVE_FACTORY, null, false,
                    want, want, 1, null, 5L, TimeUnit.SECONDS);
            solvePoolThreads = want;
        }
        return solvePool;
    }

}
