package oas.work.lethalbreed.ai;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LethalThreads {
    private static final ExecutorService POOL = Executors.newVirtualThreadPerTaskExecutor();

    public static void execute(Runnable task) {
        POOL.execute(task);
    }
    
    public static void shutdown() {
        POOL.close();
        try {
            POOL.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}