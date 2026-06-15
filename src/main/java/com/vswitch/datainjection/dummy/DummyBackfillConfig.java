package com.vswitch.datainjection.dummy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DummyBackfillConfig {

    @Bean(destroyMethod = "shutdown")
    ExecutorService dummyDeviceBackfillExecutor(
            @Value("${dummy.history.backfill.threads:4}") int threads) {
        int poolSize = Math.max(1, threads);
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory =
                runnable -> {
                    Thread thread =
                            new Thread(
                                    runnable,
                                    "dummy-history-backfill-"
                                            + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                };
        return Executors.newFixedThreadPool(poolSize, factory);
    }
}
