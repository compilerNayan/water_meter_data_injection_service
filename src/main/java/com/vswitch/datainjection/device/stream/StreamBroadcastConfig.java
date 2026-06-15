package com.vswitch.datainjection.device.stream;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StreamBroadcastConfig {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "shutdownNow")
    ScheduledExecutorService waterFlowBroadcastScheduler() {
        return Executors.newSingleThreadScheduledExecutor(
                runnable -> {
                    Thread thread = new Thread(runnable, "water-flow-broadcast");
                    thread.setDaemon(true);
                    return thread;
                });
    }
}
