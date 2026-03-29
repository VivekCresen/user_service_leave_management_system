package com.cresensolutions.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "auditTaskExecutor")
    public Executor auditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("auth-audit-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "dashboardTaskExecutor")
    public Executor dashboardTaskExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(2, processors));
        executor.setMaxPoolSize(Math.max(4, processors * 2));
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("user-dashboard-");
        executor.initialize();
        return executor;
    }
}
