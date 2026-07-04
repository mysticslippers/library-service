package me.ifmo.backend.services.impl;

import me.ifmo.backend.entities.Notification;
import me.ifmo.backend.services.NotificationDeliveryClient;
import me.ifmo.backend.services.NotificationDeliveryResult;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockNotificationDeliveryClient implements NotificationDeliveryClient {

    @Override
    public NotificationDeliveryResult send(Notification notification) {
        return new NotificationDeliveryResult(true, "mock-" + UUID.randomUUID(), null);
    }
}
