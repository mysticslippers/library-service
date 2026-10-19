package me.ifmo.backend.notification.application;

public interface NotificationDeliveryClient {

    NotificationDeliveryResult send(NotificationDeliveryTask task);
}
