package me.ifmo.backend.notification.application;

import me.ifmo.backend.notification.domain.Notification;

public interface NotificationDeliveryClient {

    NotificationDeliveryResult send(Notification notification);
}
