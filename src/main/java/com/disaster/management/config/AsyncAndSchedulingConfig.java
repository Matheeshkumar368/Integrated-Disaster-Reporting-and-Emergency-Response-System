package com.disaster.management.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Controlled Thread Pool Configuration for Async Tasks and Scheduled Jobs.
 * Guarantees graceful shutdown and deterministic resource release when
 * the application terminates (e.g. via Ctrl+C, terminal exit, or future desktop app wrapper).
 */
@Configuration
public class AsyncAndSchedulingConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncAndSchedulingConfig.class);

    @Bean(name = "applicationTaskExecutor")
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("disaster-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        log.info("Initialized managed applicationTaskExecutor with graceful termination timeout: 5s");
        return executor;
    }

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("disaster-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.initialize();
        log.info("Initialized managed taskScheduler with graceful termination timeout: 5s");
        return scheduler;
    }
}
