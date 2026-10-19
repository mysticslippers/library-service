package me.ifmo.backend.notification.application;

import me.ifmo.backend.notification.domain.enums.NotificationChannel;

public interface NotificationChannelSender {

    NotificationChannel channel();

    NotificationDeliveryResult send(NotificationDeliveryTask task);
}
