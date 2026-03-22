package com.incoin.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfig {

    /**
     * Shared executor for per-user grab worker threads.
     * destroyMethod = "shutdownNow" ensures clean JVM shutdown.
     */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService grabExecutor() {
        // CachedThreadPool: spawns a thread per user, reuses idle ones.
        // For Java 21+, replace with Executors.newVirtualThreadPerTaskExecutor()
        return Executors.newCachedThreadPool();
    }
}
