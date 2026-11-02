package me.ifmo.backend.notification.application.delivery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.ifmo.backend.notification.application.NotificationDeliveryService;
import me.ifmo.backend.notification.configuration.NotificationDeliverySchedulerProperties;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class NotificationDeliveryScheduler {

    private final NotificationDeliveryService deliveryService;
    private final NotificationDeliverySchedulerProperties properties;

    @Scheduled(scheduler = "notificationDeliveryTaskScheduler",
            fixedDelayString = "${notification.delivery.scheduler.poll-interval-ms:10000}",
            initialDelayString = "${notification.delivery.scheduler.initial-delay-ms:10000}")
    public void deliverPending() {
        try {
            deliveryService.processPending(null, properties.batchSize());
        } catch (RuntimeException error) {
            log.warn("Notification delivery batch failed: {}", error.getClass().getSimpleName());
        }
    }
}
