package me.ifmo.backend.notification.configuration;

import me.ifmo.backend.notification.application.NotificationDeliveryService;
import me.ifmo.backend.notification.application.delivery.NotificationDeliveryScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationDeliverySchedulerProperties.class)
@ConditionalOnProperty(prefix = "notification.delivery.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationDeliverySchedulingConfiguration {

    @Bean
    public ThreadPoolTaskScheduler notificationDeliveryTaskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.poolSize(1).threadNamePrefix("notification-delivery-").build();
    }

    @Bean
    public NotificationDeliveryScheduler notificationDeliveryScheduler(NotificationDeliveryService deliveryService, NotificationDeliverySchedulerProperties properties) {
        return new NotificationDeliveryScheduler(deliveryService, properties);
    }
}
