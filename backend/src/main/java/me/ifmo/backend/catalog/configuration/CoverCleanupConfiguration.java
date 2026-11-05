package me.ifmo.backend.catalog.configuration;

import me.ifmo.backend.catalog.application.cover.CoverCleanupScheduler;
import me.ifmo.backend.catalog.application.cover.CoverCleanupService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CoverCleanupProperties.class)
public class CoverCleanupConfiguration {

    @Bean
    @ConditionalOnProperty(name = "storage.covers.cleanup.enabled", havingValue = "true", matchIfMissing = true)
    ThreadPoolTaskScheduler coverCleanupTaskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.poolSize(1).threadNamePrefix("cover-cleanup-").build();
    }

    @Bean
    @ConditionalOnProperty(name = "storage.covers.cleanup.enabled", havingValue = "true", matchIfMissing = true)
    CoverCleanupScheduler coverCleanupScheduler(CoverCleanupService cleanup, CoverCleanupProperties properties) {
        return new CoverCleanupScheduler(cleanup, properties);
    }
}
