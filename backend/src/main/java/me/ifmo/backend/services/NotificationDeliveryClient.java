package me.ifmo.backend.services;

import me.ifmo.backend.entities.Notification;

public interface NotificationDeliveryClient {

    NotificationDeliveryResult send(Notification notification);
}
