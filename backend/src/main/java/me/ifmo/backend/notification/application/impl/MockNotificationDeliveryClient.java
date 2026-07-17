package me.ifmo.backend.notification.application.impl;

import me.ifmo.backend.notification.domain.Notification;
import me.ifmo.backend.notification.application.NotificationDeliveryClient;
import me.ifmo.backend.notification.application.NotificationDeliveryResult;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockNotificationDeliveryClient implements NotificationDeliveryClient {

    @Override
    public NotificationDeliveryResult send(Notification notification) {
        return new NotificationDeliveryResult(true, "mock-" + UUID.randomUUID(), null);
    }
}
