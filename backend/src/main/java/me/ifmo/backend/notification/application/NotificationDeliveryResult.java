package me.ifmo.backend.notification.application;

public record NotificationDeliveryResult(
        boolean success,
        String externalMessageId,
        String errorMessage
) {
}
